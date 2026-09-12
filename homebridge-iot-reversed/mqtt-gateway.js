'use strict';

const mqtt = require('mqtt');
const fs = require('node:fs');

class MqttGateway {
  /** Loads broker credentials from a service-readable file without logging them. */
  constructor(config, log) {
    this.options = JSON.parse(fs.readFileSync(config.mqttConfigPath, 'utf8'));
    this.log = log;
    this.root = config.topicRoot || 'iot-hub';
  }

  /** Connects a clean session and resubscribes to state and discovery after reconnecting. */
  start(onMessage, onOffline) {
    this.client = mqtt.connect(this.options.brokerUri.replace(/^tcp:/, 'mqtt:').replace(/^ssl:/, 'mqtts:'), {
      username: this.options.username,
      password: this.options.password,
      clientId: `homebridge-iot-${process.pid}`,
      clean: true,
      reconnectPeriod: 3000,
      connectTimeout: 10000,
      queueQoSZero: false,
    });
    this.client.on('connect', () => {
      this.client.subscribe([
        `${this.root}/registry/devices`, `${this.root}/+/discovery`,
        `${this.root}/+/availability`, `${this.root}/availability`,
        `${this.root}/+/+/+/state`,
      ], {qos: 1}, error => {
        if (error) this.log.error('MQTT subscription failed:', error.message);
        else this.log.info('MQTT discovery subscribed');
      });
    });
    this.client.on('message', (topic, payload) => onMessage(topic, payload.toString()));
    this.client.on('offline', onOffline);
    this.client.on('error', error => this.log.error('MQTT:', error.message));
  }

  /** Sends a non-retained command only while connected, with no offline command queue. */
  async publish(topic, payload) {
    if (!this.client?.connected) throw new Error('MQTT is offline');
    await this.client.publishAsync(topic, payload, {qos: 0, retain: false});
  }

  /** Closes the MQTT connection during Homebridge shutdown. */
  stop() { this.client?.end(true); }
}

module.exports = MqttGateway;
