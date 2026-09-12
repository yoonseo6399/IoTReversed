'use strict';

const fs = require('node:fs');
const mqtt = require('../../homebridge-iot-reversed/node_modules/mqtt');

/** Reads a fresh lamp status over MQTT using credentials from devices.json; never publishes control. */
async function verify() {
  const config = JSON.parse(fs.readFileSync('/etc/iot-reversed/devices.json', 'utf8'));
  const device = config.devices.find(d => d.id === (process.argv[2] || 'my-room'));
  if (!device) throw new Error('Unknown configured device');
  const root = config.topicRoot || 'iot-hub';
  const client = await mqtt.connectAsync(config.mqtt.brokerUri.replace(/^tcp:/, 'mqtt:').replace(/^ssl:/, 'mqtts:'), {
    username: config.mqtt.username, password: config.mqtt.password,
    clientId: `iot-read-verification-${process.pid}`, clean: true, reconnectPeriod: 0,
  });
  try {
    let requested = false;
    let resolveState, rejectState;
    const state = new Promise((resolve, reject) => { resolveState = resolve; rejectState = reject; });
    const timeout = setTimeout(() => rejectState(new Error('No fresh lamp state within 15 seconds')), 15000);
    client.on('message', (topic, payload, packet) => {
      const value = payload.toString();
      if (topic.endsWith('/availability')) {
        console.log(`${topic}=${value} retained=${packet.retain}`);
        if (value === 'panicked') rejectState(new Error('PANICKED: stop debugging and inspect device'));
      }
      if (requested && !packet.retain && topic === `${root}/${device.id}/lamp/1/state`) {
        console.log(`${topic}=${value} retained=false (fresh BLE status)`);
        resolveState(value);
      }
    });
    await client.subscribeAsync([`${root}/${device.id}/lamp/1/state`, `${root}/${device.id}/availability`], {qos: 1});
    requested = true;
    await client.publishAsync(`${root}/${device.id}/status/get`, '', {qos: 1, retain: false});
    try {
      const value = await state;
      if (value !== 'OFF') throw new Error(`Expected the user's lamp to be OFF, received ${value}`);
    } finally { clearTimeout(timeout); }
  } finally { await client.endAsync(); }
}

verify().catch(error => { console.error(error.message); process.exitCode = 1; });
