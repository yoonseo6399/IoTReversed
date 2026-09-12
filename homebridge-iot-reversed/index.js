'use strict';

const MqttGateway = require('./mqtt-gateway');
const PLUGIN = 'homebridge-iot-reversed';
const PLATFORM = 'IoTReversed';

class IoTReversedPlatform {
  /** Creates a dynamic platform; MQTT discovery starts only after cached accessories are restored. */
  constructor(log, config, api, Gateway = MqttGateway) {
    this.log = log;
    this.config = config;
    this.api = api;
    this.root = config.topicRoot || 'iot-hub';
    this.accessories = new Map();
    this.registry = null;
    this.descriptions = new Map();
    this.availability = new Map();
    this.states = new Map();
    this.hubOnline = false;
    this.gateway = new Gateway(config, log);
    api.on('didFinishLaunching', () => this.gateway.start(
      (topic, payload) => this.receive(topic, payload),
      () => { this.hubOnline = false; this.updateHealth(); },
    ));
    api.on('shutdown', () => this.gateway.stop());
  }

  /** Restores stable accessory UUIDs without assuming cached states are still online. */
  configureAccessory(accessory) {
    this.accessories.set(accessory.UUID, accessory);
    this.configureModule(accessory);
  }

  /** Validates registry/discovery messages and reconciles accessories regardless of retained-message order. */
  receive(topic, payload) {
    try {
      if (!topic.startsWith(`${this.root}/`)) return;
      const parts = topic.slice(this.root.length + 1).split('/');
      if (parts.join('/') === 'registry/devices') {
        const devices = JSON.parse(payload);
        if (!Array.isArray(devices) || devices.some(d => !/^[A-Za-z0-9_-]+$/.test(d.id))) throw new Error('Invalid registry');
        this.registry = new Map(devices.map(d => [d.id, d]));
        for (const accessory of this.accessories.values()) {
          if (!this.registry.has(accessory.context.deviceId)) this.remove(accessory);
        }
        for (const id of this.descriptions.keys()) this.reconcile(id);
      } else if (parts.length === 2 && parts[1] === 'discovery') {
        if (!payload) { this.descriptions.delete(parts[0]); return; }
        const device = JSON.parse(payload);
        if (device.id !== parts[0] || !Array.isArray(device.modules) || device.modules.length > 32 ||
            device.modules.some(m => !['lamp', 'outlet'].includes(m.type) || !Number.isInteger(m.number) || m.number < 1)) {
          throw new Error('Invalid discovery');
        }
        this.descriptions.set(device.id, device);
        this.reconcile(device.id);
      } else if (parts.join('/') === 'availability') {
        this.hubOnline = payload === 'online';
        this.updateHealth();
      } else if (parts.length === 2 && parts[1] === 'availability') {
        this.availability.set(parts[0], payload);
        if (payload === 'panicked') this.log.error(`${parts[0]}: panicked; BLE requests stopped`);
        this.updateHealth();
      } else if (parts.length === 4 && parts[3] === 'state' && ['ON', 'OFF'].includes(payload)) {
        this.states.set(parts.slice(0, 3).join('/'), payload === 'ON');
        this.updateHealth();
      }
    } catch (error) {
      this.log.warn(`Ignored MQTT message ${topic}: ${error.message}`);
    }
  }

  /** Adds/removes modules from authoritative registry and BLE discovery, preserving UUIDs. */
  reconcile(id) {
    const registered = this.registry?.get(id);
    const description = this.descriptions.get(id);
    if (!registered || !description) return;
    const wanted = new Set();
    for (const module of description.modules) {
      const key = `${id}/${module.type}/${module.number}`;
      const uuid = this.api.hap.uuid.generate(`${PLUGIN}:${this.root}:${key}`);
      wanted.add(uuid);
      let accessory = this.accessories.get(uuid);
      const name = `${registered.displayName || id} ${module.type} ${module.number}`;
      const isNew = !accessory;
      if (!accessory) accessory = new this.api.platformAccessory(name, uuid);
      accessory.displayName = name;
      accessory.context = {deviceId: id, type: module.type, number: module.number, key};
      this.configureModule(accessory);
      this.accessories.set(uuid, accessory);
      if (isNew) {
        this.api.registerPlatformAccessories(PLUGIN, PLATFORM, [accessory]);
        this.log.info(`Added ${name}`);
      } else this.api.updatePlatformAccessories([accessory]);
    }
    for (const accessory of this.accessories.values()) {
      if (accessory.context.deviceId === id && !wanted.has(accessory.UUID)) this.remove(accessory);
    }
    this.updateHealth();
  }

  /** Binds HomeKit reads to MQTT state and blocks writes when the hub or device is unavailable. */
  configureModule(accessory) {
    const {Service, Characteristic: C} = this.api.hap;
    const context = accessory.context;
    const serviceType = context.type === 'lamp' ? Service.Lightbulb : Service.Outlet;
    const service = accessory.getService(serviceType) || accessory.addService(serviceType, accessory.displayName);
    accessory.getService(Service.AccessoryInformation)
      .setCharacteristic(C.Manufacturer, 'IoTReversed')
      .setCharacteristic(C.Model, 'BLE MQTT switch')
      .setCharacteristic(C.SerialNumber, context.key);
    service.setCharacteristic(C.Name, accessory.displayName);
    service.getCharacteristic(C.On)
      .onGet(() => {
        if (!this.isOnline(context.deviceId) || !this.states.has(context.key)) throw this.unavailable();
        return this.states.get(context.key);
      })
      .onSet(async value => {
        if (!this.isOnline(context.deviceId)) throw this.unavailable();
        await this.gateway.publish(`${this.root}/${context.key}/set`, value ? 'ON' : 'OFF');
      });
    if (context.type === 'outlet') service.setCharacteristic(C.OutletInUse, true);
  }

  /** Combines daemon availability and device availability; panic always blocks interaction. */
  isOnline(id) { return this.hubOnline && this.availability.get(id) === 'online'; }

  /** Builds the standard HomeKit communication error for offline or panicked devices. */
  unavailable() { return new this.api.hap.HapStatusError(this.api.hap.HAPStatus.SERVICE_COMMUNICATION_FAILURE); }

  /** Updates HomeKit values or communication faults without sending any BLE control commands. */
  updateHealth() {
    const {Service, Characteristic: C} = this.api.hap;
    for (const accessory of this.accessories.values()) {
      const {deviceId, type, key} = accessory.context;
      const service = accessory.getService(type === 'lamp' ? Service.Lightbulb : Service.Outlet);
      service.updateCharacteristic(C.On, this.isOnline(deviceId) && this.states.has(key)
        ? this.states.get(key) : this.unavailable());
    }
  }

  /** Removes only accessories owned by this platform. */
  remove(accessory) {
    this.api.unregisterPlatformAccessories(PLUGIN, PLATFORM, [accessory]);
    this.accessories.delete(accessory.UUID);
  }
}

/** Registers the local MQTT dynamic platform with Homebridge. */
module.exports = api => api.registerPlatform(PLUGIN, PLATFORM, IoTReversedPlatform);
module.exports.IoTReversedPlatform = IoTReversedPlatform;
