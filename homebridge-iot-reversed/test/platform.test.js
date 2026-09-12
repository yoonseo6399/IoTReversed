'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {IoTReversedPlatform} = require('../index');

class Service {
  /** Provides a minimal HomeKit service for observing handlers and state updates. */
  constructor() { this.values = new Map(); this.handlers = {}; }
  /** Records metadata. */
  setCharacteristic(key, value) { this.values.set(key, value); return this; }
  /** Records state and error propagation. */
  updateCharacteristic(key, value) { this.values.set(key, value); return this; }
  /** Exposes registered HomeKit callbacks. */
  getCharacteristic() {
    return {onGet: fn => { this.handlers.get = fn; return {onSet: fn => { this.handlers.set = fn; }}; }};
  }
}
class Accessory {
  /** Creates an accessory with required information service. */
  constructor(name, uuid) { this.displayName = name; this.UUID = uuid; this.context = {}; this.services = new Map([['info', new Service()]]); }
  /** Resolves services by type. */
  getService(type) { return this.services.get(type); }
  /** Adds a module service. */
  addService(type) { const service = new Service(); this.services.set(type, service); return service; }
}
class Gateway {
  /** Records published commands without networking. */
  constructor() { this.sent = []; }
  /** Captures explicit HomeKit writes only. */
  async publish(topic, payload) { this.sent.push({topic, payload}); }
}

/** Builds an isolated platform and API recorder. */
function setup() {
  const registered = [], removed = [];
  const api = {
    hap: {Service: {AccessoryInformation: 'info', Lightbulb: 'lamp', Outlet: 'outlet'},
      Characteristic: {On: 'On', Name: 'Name'}, uuid: {generate: key => key},
      HapStatusError: Error, HAPStatus: {SERVICE_COMMUNICATION_FAILURE: -70402}},
    platformAccessory: Accessory, on() {}, updatePlatformAccessories() {},
    registerPlatformAccessories: (_, __, items) => registered.push(...items),
    unregisterPlatformAccessories: (_, __, items) => removed.push(...items),
  };
  const log = {info() {}, warn() {}, error() {}};
  const platform = new IoTReversedPlatform(log, {topicRoot: 'iot-hub'}, api, Gateway);
  return {platform, registered, removed};
}

test('retained discovery order, duplicate registration, removal and stale discovery', () => {
  const {platform, registered, removed} = setup();
  const device = {id: 'my-room', modules: [{type: 'lamp', number: 1}, {type: 'outlet', number: 1}]};
  platform.receive('iot-hub/my-room/discovery', JSON.stringify(device));
  assert.equal(registered.length, 0);
  platform.receive('iot-hub/registry/devices', JSON.stringify([{id: 'my-room', displayName: '내방'}]));
  assert.equal(registered.length, 2);
  platform.receive('iot-hub/my-room/discovery', JSON.stringify(device));
  assert.equal(registered.length, 2);
  platform.receive('iot-hub/registry/devices', '[]');
  assert.equal(removed.length, 2);
  platform.receive('iot-hub/my-room/discovery', JSON.stringify(device));
  assert.equal(platform.accessories.size, 0);
});

test('reads OFF without publishing controls; panicked blocks HomeKit writes and reads', async () => {
  const {platform, registered} = setup();
  platform.receive('iot-hub/registry/devices', '[{"id":"my-room"}]');
  platform.receive('iot-hub/my-room/discovery', '{"id":"my-room","modules":[{"type":"lamp","number":1}]}');
  platform.receive('iot-hub/availability', 'online');
  platform.receive('iot-hub/my-room/availability', 'online');
  platform.receive('iot-hub/my-room/lamp/1/state', 'OFF');
  const service = registered[0].getService('lamp');
  assert.equal(service.handlers.get(), false);
  assert.equal(platform.gateway.sent.length, 0);
  platform.receive('iot-hub/my-room/availability', 'panicked');
  assert.throws(() => service.handlers.get());
  await assert.rejects(() => service.handlers.set(true));
  assert.equal(platform.gateway.sent.length, 0);
  assert.ok(service.values.get('On') instanceof Error);
});
