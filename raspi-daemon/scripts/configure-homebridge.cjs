'use strict';

const fs = require('node:fs');
const path = require('node:path');

/** Adds only this platform and stores a private copy of MQTT credentials, preserving existing plugins. */
function configure() {
  const hubPath = '/etc/iot-reversed/devices.json';
  const destination = '/var/lib/homebridge/config.json';
  const credentials = '/var/lib/homebridge/iot-mqtt.json';
  const hub = JSON.parse(fs.readFileSync(hubPath, 'utf8'));
  const config = JSON.parse(fs.readFileSync(destination, 'utf8'));
  const owner = fs.statSync(destination);
  const backup = `${destination}.iot-reversed-${Date.now()}.bak`;
  fs.copyFileSync(destination, backup, fs.constants.COPYFILE_EXCL);
  fs.chmodSync(backup, 0o600);
  fs.writeFileSync(credentials, JSON.stringify(hub.mqtt, null, 2), {mode: 0o600});
  fs.chmodSync(credentials, 0o600);
  fs.chownSync(credentials, owner.uid, owner.gid);
  const platform = {platform: 'IoTReversed', name: 'IoTReversed', topicRoot: hub.topicRoot || 'iot-hub', mqttConfigPath: credentials};
  config.platforms = [...(config.platforms || []).filter(p => p.platform !== 'IoTReversed'), platform];
  const temporary = path.join(path.dirname(destination), `.iot-config-${process.pid}.json`);
  fs.writeFileSync(temporary, JSON.stringify(config, null, 2), {mode: 0o600, flag: 'wx'});
  fs.chownSync(temporary, owner.uid, owner.gid);
  fs.renameSync(temporary, destination);
  console.log(`Homebridge platform configured; backup=${backup}`);
}

configure();
