package io.github.yoonseo6399

data class Packet(val cmd : Int,val payload : List<Int>)
fun uartRxParser(bArr: ByteArray): Packet? {
    // 1. 최소 길이 확인 (헤더 5 + 체크섬 2 = 7바이트는 최소한 있어야 함)
    if (bArr.size < 7) return null

    val header1 = bArr[0].toInt() and 0xFF
    val header2 = bArr[1].toInt() and 0xFF
    val header3 = bArr[2].toInt() and 0xFF
    val dataLength = bArr[4].toInt() and 0xFF // b6 역할
    val cmd = bArr[3].toInt() and 0xFF

    // 2. 헤더 조건 검사 (0x7E, 0x10, 0x0F)
    if (header1 != 0x7E || header2 != 0x10 || header3 != 0x0F) {
        return null
    }

    // 3. 체크섬 계산 범위 설정
    val payloadEndIdx = dataLength + 5
    var xorChecksum = 0
    var addChecksum = 0
    var xorSum = 0
    var addSum = 0
    for (i in 0 until payloadEndIdx) {
        val current = bArr[i].toInt() and 0xFF
        xorSum = xorSum xor current
        addSum = (addSum + current) and 0xFF
    }

    // 4. 체크섬 비교
    val receivedXor = bArr[payloadEndIdx].toInt() and 0xFF
    val receivedCombined = bArr[payloadEndIdx + 1].toInt() and 0xFF
    val calculatedCombined = (addSum + xorSum) and 0xFF

    if (xorSum != receivedXor || calculatedCombined != receivedCombined) {
        println("ERR: Checksum Mismatch")
        return null
    }

    // 5. 실제 데이터 추출 (5번 인덱스부터 dataLength 만큼)
    val data = bArr.sliceArray(5 until 5 + dataLength)
        .map { it.toInt() and 0xFF }

    return Packet(cmd,data)
}
/**
fun rxAnalyzerPass1(bArr: ByteArray) {
    var i: Int
    val cArr = CharArray(20)
    var b: Byte = 0
    val b2 = bArr[0]
    val b3 = bArr[1]
    val b4 = bArr[2]
    val b5 = bArr[3]
    val b6 = bArr[4]
    if (b2.toInt() == 126 && b3.toInt() == 16 && b4.toInt() == 15) {
        var b7: Byte = 0
        var b8: Byte = 0
        var b9: Byte = 0
        while (true) {
            i = b6 + 5
            if (b7 >= i) {
                break
            }
            val b10 = bArr[b7.toInt()]
            b9 = (b9.toInt() xor b10.toInt()).toByte()
            b8 = (b8 + b10).toByte()
            b7 = (b7 + 1).toByte()
        }
        val b11 = (b8 + b9).toByte()
        if (b9 != bArr[i] || b11 != bArr[b6 + 6]) {
            var b12: Byte = 0
            while (b12 < b6) {
                cArr[b12.toInt()] = (bArr[b12 + 5].toInt() and 255).toChar()
                b12 = (b12 + 1).toByte()
            }
            this.cmdBefore = 0
            if (b5.toInt() == 49) {
                this.txDcommSetLamp = false
                println("txDcommSetLamp clear ")
            } else if (b5.toInt() == 54) {
                DeviceControlActivity.TxDcommSetPatten = false
                println("TxDcommSetPatten clear ")
            } else if (b5.toInt() == 97) {
                DeviceControlActivity.txDcommReqTempStatus = false
                println("txDcommReqTempStatus clear ")
            } else if (b5.toInt() == 121) {
                this.txDcommTest1 = false
                println("txDcommTest1 clear ")
                Log.d(
                    DeviceControlActivity.TAG,
                    "cmd=" + Integer.toHexString(b5.toInt()) + " len=" + b6 + " data=" + Integer.toHexString(cArr[0].code) + Integer.toHexString(
                        cArr[1].code
                    ) + Integer.toHexString(cArr[2].code) + Integer.toHexString(cArr[3].code)
                )
            } else if (b5.toInt() != 51) {
                var str = ""
                if (b5.toInt() == 52) {
                    this.txDcommReqDeviceInfo = false
                    println("2)txDcommReqDeviceInfo clear ")
                    this.strVerBLE = str
                    if (b6.toInt() == 0) {
                        this.strVerBLE = "00"
                        Log.d(
                            DeviceControlActivity.TAG,
                            "IoT Switch Version(BLE) : 이전버전 V" + this.strVerSwitch + " len=" + b6
                        )
                    } else if (b6.toInt() == 1) {
                        this.strVerBLE += cArr[0]
                        Log.d(
                            DeviceControlActivity.TAG,
                            "IoT Switch Version(BLE) : 이전버전 V" + this.strVerBLE + " len=" + b6
                        )
                        this.strVerBLE = "00"
                    } else {
                        while (b < b6) {
                            if (b.toInt() == 2) {
                                this.strVerBLE += " "
                            }
                            this.strVerBLE += cArr[b.toInt()]
                            b = (b + 1).toByte()
                        }
                        println("IoT Switch Version(BLE) : V" + this.strVerBLE + " len=" + b6)
                    }
                } else if (b5.toInt() == 112) {
                    println("txDcommReqOtherDevInfo clear ")
                    this.txDcommReqOtherDevInfo = false
                    var b13: Byte = 0
                    while (b13 < b6) {
                        str = str + Integer.toHexString(cArr[b13.toInt()].code)
                        b13 = (b13 + 1).toByte()
                    }
                    println("Rx(CMD2_REQ_OTHER_DEV): " + str)
                    if (b6 < 6) {
                        println("Data invalid length=" + b6)
                        DeviceControlActivity.enableDevTemp = false
                        DeviceControlActivity.enableDevAircon = false
                        DeviceControlActivity.isAddTempAircon = false
                        return
                    }
                    val c = cArr[0]
                    if (c.code > 4) {
                        this.lampCount = '4'
                    } else {
                        this.lampCount = (c.code + '0'.code).toChar()
                    }
                    println("lampCount=" + this.lampCount)
                    if (cArr[2].code == 1) {
                        DeviceControlActivity.enableDevTemp = true
                    } else {
                        DeviceControlActivity.enableDevTemp = false
                    }
                    if (cArr[3].code == 1) {
                        DeviceControlActivity.enableDevAircon = true
                    } else {
                        DeviceControlActivity.enableDevAircon = false
                    }
                    if (cArr[4].code == 1) {
                        DeviceControlActivity.enableDevFan = true
                    } else {
                        DeviceControlActivity.enableDevFan = false
                    }
                    if (DeviceControlActivity.enableDevAircon || DeviceControlActivity.enableDevTemp || DeviceControlActivity.enableDevFan) {
                        DeviceControlActivity.isAddTempAircon = true
                    }
                    if (b6 > 6) {
                        println("Switch Model = " + Integer.toHexString(cArr[6].code))
                    }
                    DeviceControlActivity.isCommFirstEnd = true
                } else if (b5.toInt() != 113) {
                    when (b5) {
                        DeviceControlActivity.CMD_REQ_SET_WAKE -> {
                            DeviceControlActivity.TxDcommSetAlarm = false
                            println("TxDcommSetSetAlarm clear ")
                            return
                        }

                        60 -> {
                            DeviceControlActivity.TxDcommSetSleep = false
                            println("TxDcommSetSleep clear ")
                            return
                        }

                        61 -> {
                            DeviceControlActivity.TxDcommSetBang = false
                            println("TxDcommSetBang clear ")
                            return
                        }

                        62 -> {
                            this.txDcommReqTimeInfo = false
                            println("4)txDcommReqTimeInfo clear ")
                            if (DeviceControlActivity.enableDevTemp) {
                                DeviceControlActivity.txDcommReqTempStatus = true
                            } else if (DeviceControlActivity.enableDevAircon) {
                                DeviceControlActivity.txDcommReqAirconStatus = true
                            } else if (DeviceControlActivity.enableDevFan) {
                                DeviceControlActivity.txDcommReqFanStatus = true
                            }
                            DeviceControlActivity.isCommInitEnd = true
                            println("5)IoT Switch InitComm End ")
                            val b14: Byte = 0
                            while (b14 < 12) {
                                str = str + Integer.toHexString(cArr[b14.toInt()].code)
                                b14 = (b14 + 1).toByte()
                            }
                            println("Rx(CMD_REQ_TIMEINFO): " + str)
                            DeviceControlActivity.strTimeAlarm1 =
                                String.format("%01x", *arrayOf<Any?>(cArr[0].code shr 4))
                            DeviceControlActivity.strTimeAlarm1 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[0].code and 15)
                            )
                            DeviceControlActivity.strTimeAlarm1 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[1].code shr 4)
                            )
                            DeviceControlActivity.strTimeAlarm1 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[1].code and 15)
                            )
                            DeviceControlActivity.strTimeAlarm2 =
                                String.format("%01x", *arrayOf<Any?>(cArr[2].code shr 4))
                            DeviceControlActivity.strTimeAlarm2 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[2].code and 15)
                            )
                            DeviceControlActivity.strTimeAlarm2 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[3].code shr 4)
                            )
                            DeviceControlActivity.strTimeAlarm2 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[3].code and 15)
                            )
                            DeviceControlActivity.strTimeAlarm3 =
                                String.format("%01x", *arrayOf<Any?>(cArr[4].code shr 4))
                            DeviceControlActivity.strTimeAlarm3 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[4].code and 15)
                            )
                            DeviceControlActivity.strTimeAlarm3 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[5].code shr 4)
                            )
                            DeviceControlActivity.strTimeAlarm3 += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[5].code and 15)
                            )
                            DeviceControlActivity.strTimeSleep =
                                String.format("%01x", *arrayOf<Any?>(cArr[6].code shr 4))
                            DeviceControlActivity.strTimeSleep += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[6].code and 15)
                            )
                            DeviceControlActivity.strTimeSleep += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[7].code shr 4)
                            )
                            DeviceControlActivity.strTimeSleep += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[7].code and 15)
                            )
                            DeviceControlActivity.strTimeSafeOn =
                                String.format("%01x", *arrayOf<Any?>(cArr[8].code shr 4))
                            DeviceControlActivity.strTimeSafeOn += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[8].code and 15)
                            )
                            DeviceControlActivity.strTimeSafeOn += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[9].code shr 4)
                            )
                            DeviceControlActivity.strTimeSafeOn += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[9].code and 15)
                            )
                            DeviceControlActivity.strTimeSafeOff =
                                String.format("%01x", *arrayOf<Any?>(cArr[10].code shr 4))
                            DeviceControlActivity.strTimeSafeOff += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[10].code and 15)
                            )
                            DeviceControlActivity.strTimeSafeOff += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[11].code shr 4)
                            )
                            DeviceControlActivity.strTimeSafeOff += String.format(
                                "%01x",
                                *arrayOf<Any?>(cArr[11].code and 15)
                            )
                            if (FragmentCtlConvAlarm.AlarmNew) {
                                if (DeviceControlActivity.strTimeAlarm1 == "0000") {
                                    FragmentCtlConvAlarm.select1 = false
                                } else {
                                    FragmentCtlConvAlarm.select1 = true
                                }
                                if (DeviceControlActivity.strTimeAlarm2 == "0000") {
                                    FragmentCtlConvAlarm.select2 = false
                                } else {
                                    FragmentCtlConvAlarm.select2 = true
                                }
                                if (DeviceControlActivity.strTimeAlarm3 == "0000") {
                                    FragmentCtlConvAlarm.select3 = false
                                } else {
                                    FragmentCtlConvAlarm.select3 = true
                                }
                                if (FragmentCtlConvAlarm.select1 || FragmentCtlConvAlarm.select2 || FragmentCtlConvAlarm.select3) {
                                    FragmentCtlConvAlarm.enableAlarm = true
                                } else {
                                    FragmentCtlConvAlarm.enableAlarm = false
                                }
                            } else if ((DeviceControlActivity.strTimeAlarm1 != "0000") || (DeviceControlActivity.strTimeAlarm2 != "0000") || (DeviceControlActivity.strTimeAlarm3 != "0000")) {
                                FragmentCtlConvAlarm.enableAlarm = true
                            } else {
                                FragmentCtlConvAlarm.enableAlarm = false
                            }
                            if (DeviceControlActivity.strTimeSleep == "0000") {
                                FragmentCtlConvSleep.enableSleep = false
                            } else {
                                FragmentCtlConvSleep.enableSleep = true
                            }
                            if (DeviceControlActivity.strTimeSafeOn != "0000" || DeviceControlActivity.strTimeSafeOff != "0000") {
                                FragmentCtlConvSafe.enableSafe = true
                            } else {
                                FragmentCtlConvSafe.enableSafe = false
                            }
                            Log.d(
                                DeviceControlActivity.TAG,
                                "방범시간: " + DeviceControlActivity.strTimeSafeOn + " " + DeviceControlActivity.strTimeSafeOff
                            )
                            Log.d(
                                DeviceControlActivity.TAG,
                                "알람시간: " + DeviceControlActivity.strTimeAlarm1 + " " + DeviceControlActivity.strTimeAlarm2 + " " + DeviceControlActivity.strTimeAlarm3 + " 취침시간: " + DeviceControlActivity.strTimeSleep + "방범시간: " + DeviceControlActivity.strTimeSafeOn + " " + DeviceControlActivity.strTimeSafeOff
                            )
                            return
                        }

                        63 -> {
                            this.txDcommSetWatch = false
                            DeviceControlActivity.TxCnt = 0
                            println("1)txDcommSetWatch clear ")
                            this.strVerSwitch = str
                            if (b6.toInt() == 0) {
                                this.strVerSwitch = "00"
                                Log.d(
                                    DeviceControlActivity.TAG,
                                    "IoT Switch Version(ST) : 이전버전 V" + this.strVerSwitch + " len=" + b6
                                )
                            } else if (b6.toInt() == 1) {
                                this.strVerSwitch += cArr[0]
                                Log.d(
                                    DeviceControlActivity.TAG,
                                    "IoT Switch Version(ST) : 이전버전 V" + this.strVerSwitch + " len=" + b6
                                )
                                this.strVerSwitch = "00"
                            } else {
                                while (b < b6) {
                                    if (b.toInt() == 1) {
                                        this.strVerSwitch += " "
                                    }
                                    if (b.toInt() == 0) {
                                        this.strVerSwitch += (((cArr[b.toInt()].code shr 4) + 48).toChar())
                                        this.strVerSwitch += (((cArr[b.toInt()].code and 15) + '0'.code).toChar())
                                    } else {
                                        this.strVerSwitch += cArr[b.toInt()]
                                    }
                                    b = (b + 1).toByte()
                                }
                                Log.d(
                                    DeviceControlActivity.TAG,
                                    "IoT Switch Version(ST) : V" + this.strVerSwitch + " len=" + b6
                                )
                            }
                            saveDeviceList()
                            return
                        }

                        else -> when (b5) {
                            65 -> {
                                val b15: Byte = 0
                                while (b15 < 5) {
                                    str = str + Integer.toHexString(cArr[b15.toInt()].code)
                                    b15 = (b15 + 1).toByte()
                                }
                                println("Rx(CMD2_REQ_STS_LAMP): " + str)
                                this.lampCount = (cArr[0].code + '0'.code).toChar()
                                val b16: Byte = 0
                                while (b16 < 4) {
                                    val i2 = b16 + 1
                                    this.lampState[b16.toInt()] = (cArr[i2].code + '0'.code).toChar()
                                    b16 = i2.toByte()
                                }
                                cArr[0] = 1.toChar()
                                MakeTxFramePass(65.toByte(), 1.toByte(), cArr)
                                if (this.displayIndex == R.integer.Display_CtlMain) {
                                    this.fragmentControlMain.lampDisplay()
                                }
                                if (this.displayIndex == R.integer.Display_Connecting) {
                                    DeviceControlActivity.enableDevTemp = false
                                    DeviceControlActivity.enableDevAircon = false
                                    DeviceControlActivity.isAddTempAircon = false
                                    DeviceControlActivity.enableDevFan = false
                                    DeviceControlActivity.isCommFirstEnd = true
                                    onFragmentChanged(R.integer.Display_CtlMain)
                                    println("jyk change")
                                    return
                                }
                                return
                            }

                            66 -> {
                                MakeTxFramePass(66.toByte(), 1.toByte(), cArr)
                                return
                            }

                            67 -> {
                                MakeTxFramePass(67.toByte(), 1.toByte(), cArr)
                                return
                            }

                            68 -> {
                                MakeTxFramePass(68.toByte(), 1.toByte(), cArr)
                                if (!DeviceControlActivity.isCommInitEnd) {
                                    DeviceControlActivity.txDcommReqWatt = true
                                    return
                                }
                                return
                            }

                            69 -> {
                                val str2 = str
                                val b17: Byte = 0
                                while (b17 < 8) {
                                    str2 = str2 + Integer.toHexString(cArr[b17.toInt()].code)
                                    b17 = (b17 + 1).toByte()
                                }
                                println("Rx(CMD_REQ_STS_PWR): " + str2)
                                this.strPowerVal = str
                                this.strPowerVal = String.format("%01x", *arrayOf<Any?>(cArr[4].code shr 4))
                                this.strPowerVal += String.format("%01x", *arrayOf<Any?>(cArr[4].code and 15))
                                this.strPowerVal += String.format("%01x", *arrayOf<Any?>(cArr[5].code shr 4))
                                this.strPowerVal += String.format("%01x", *arrayOf<Any?>(cArr[5].code and 15))
                                cArr[0] = 1.toChar()
                                MakeTxFramePass(69.toByte(), 1.toByte(), cArr)
                                if (this.displayIndex == R.integer.Display_CtlPower) {
                                    this.fragmentControlPower.powerDisplay()
                                }
                                if (!DeviceControlActivity.isCommInitEnd) {
                                    this.txDcommReqTimeInfo = true
                                    return
                                }
                                return
                            }

                            else -> when (b5) {
                                99 -> {
                                    DeviceControlActivity.TxDcommSetTemp = false
                                    println("TxDcommSetTemp clear ")
                                    return
                                }

                                100 -> {
                                    DeviceControlActivity.TxDcommSetExit = false
                                    println("TxDcommSetExit clear ")
                                    return
                                }

                                101 -> {
                                    MakeTxFramePass(DeviceControlActivity.CMD2_REQ_STS_TEMP, 1.toByte(), cArr)
                                    if (cArr[0].code == 0) {
                                        var str3 = str
                                        var b18: Byte = 0
                                        while (b18 < 8) {
                                            str3 = str3 + Integer.toHexString(cArr[b18.toInt()].code)
                                            b18 = (b18 + 1).toByte()
                                        }
                                        println("Rx(CMD2_REQ_STS_TEMP): " + str3)
                                        DeviceControlActivity.strTempCurrent =
                                            String.format("%01x", *arrayOf<Any?>(cArr[2].code shr 4))
                                        DeviceControlActivity.strTempCurrent += String.format(
                                            "%01x",
                                            *arrayOf<Any?>(cArr[2].code and 15)
                                        )
                                        if (cArr[1].code == 1) {
                                            DeviceControlActivity.strTempCurrent += ".5"
                                        } else {
                                            DeviceControlActivity.strTempCurrent += ".0"
                                        }
                                        DeviceControlActivity.strTempSetting =
                                            String.format("%01x", *arrayOf<Any?>(cArr[4].code shr 4))
                                        DeviceControlActivity.strTempSetting += String.format(
                                            "%01x",
                                            *arrayOf<Any?>(cArr[4].code and 15)
                                        )
                                        if (cArr[3].code == 1) {
                                            DeviceControlActivity.strTempSetting += ".5"
                                        } else {
                                            DeviceControlActivity.strTempSetting += ".0"
                                        }
                                        if (cArr[5].code == 1) {
                                            FragmentControlTemp.enableTempExit = true
                                        } else {
                                            FragmentControlTemp.enableTempExit = false
                                        }
                                        if (cArr[6].code == 1) {
                                            FragmentControlTemp.enableTempPower = true
                                        } else {
                                            FragmentControlTemp.enableTempPower = false
                                        }
                                        if (this.displayIndex == R.integer.Display_CtlTemp) {
                                            this.fragmentControlTemp.tempDisplay()
                                        }
                                        if (this.stepSelfTest == 1) {
                                            this.stepSelfTest = 2
                                            this.strErrCode = str
                                        }
                                    } else {
                                        var str4 = str
                                        var b19: Byte = 0
                                        while (b19 < 6) {
                                            str4 = str4 + Integer.toHexString(cArr[b19.toInt()].code)
                                            b19 = (b19 + 1).toByte()
                                        }
                                        println("Rx(CMD2_REQ_STS_TEMP): " + str4)
                                        while (b < 4) {
                                            str = str + cArr[b + 2]
                                            b = (b + 1).toByte()
                                        }
                                        println("ErrCode=" + str)
                                        if (this.stepSelfTest == 0) {
                                            val dialogInterface: DialogInterface? = this.mPopupSelfTest1
                                            if (dialogInterface != null) {
                                                dialogInterface.dismiss()
                                            }
                                            val builder: AlertDialog.Builder = Builder(this)
                                            builder.setTitle("[고장진단]" as CharSequence)
                                            if (str == "EL01") {
                                                builder.setMessage("에러코드(난방): EL01 \n온도밸브 제어기 통신에러 \n해당 디바이스의 점검이 필요합니다" as CharSequence)
                                            } else if (str == "BL01") {
                                                builder.setMessage("에러코드: BL01 \n보일러 통신에러 \n해당 디바이스의 점검이 필요합니다" as CharSequence)
                                            } else {
                                                builder.setMessage(("에러코드: " as CharSequence).toString() + str + "\n정의되지않음 \n해당 디바이스의 점검이 필요합니다")
                                            }
                                            this.mPopupSelfTest1 = builder.show()
                                        } else {
                                            this.stepSelfTest = 2
                                            this.strErrCode = str
                                        }
                                    }
                                    if (DeviceControlActivity.enableDevAircon) {
                                        if (!DeviceControlActivity.isCommInitTemp) {
                                            DeviceControlActivity.txDcommReqAirconStatus = true
                                            DeviceControlActivity.isCommInitTemp = true
                                        }
                                    } else if (DeviceControlActivity.enableDevFan && !DeviceControlActivity.isCommInitTemp) {
                                        DeviceControlActivity.txDcommReqFanStatus = true
                                        DeviceControlActivity.isCommInitTemp = true
                                    }
                                    Log.d(
                                        DeviceControlActivity.TAG,
                                        "isAddTempAircon=" + DeviceControlActivity.isAddTempAircon + " enableDevTemp=" + DeviceControlActivity.enableDevTemp + " enableDevAircon=" + DeviceControlActivity.enableDevAircon + "enableDevFan=" + DeviceControlActivity.enableDevFan
                                    )
                                    return
                                }

                                102 -> {
                                    DeviceControlActivity.txDcommReqAirconStatus = false
                                    println("txDcommReqAirconStatus clear ")
                                    return
                                }

                                else -> when (b5) {
                                    104 -> {
                                        DeviceControlActivity.TxDcommSetTempAir = false
                                        println("TxDcommSetTempAir clear ")
                                        return
                                    }

                                    105 -> {
                                        DeviceControlActivity.TxDcommSetPwrAir = false
                                        println("TxDcommSetPwrAir clear ")
                                        return
                                    }

                                    106 -> {
                                        DeviceControlActivity.TxDcommSetWindAir = false
                                        println("TxDcommSetWindAir clear ")
                                        return
                                    }

                                    107 -> {
                                        MakeTxFramePass(DeviceControlActivity.CMD2_REQ_STS_AIRC, 1.toByte(), cArr)
                                        if (cArr[0].code == 0) {
                                            var str5 = str
                                            var b20: Byte = 0
                                            while (b20 < 8) {
                                                str5 = str5 + Integer.toHexString(cArr[b20.toInt()].code)
                                                b20 = (b20 + 1).toByte()
                                            }
                                            println("Rx(CMD2_REQ_STS_AIRC): " + str5)
                                            DeviceControlActivity.strTAirCurrent =
                                                String.format("%01x", *arrayOf<Any?>(cArr[2].code shr 4))
                                            DeviceControlActivity.strTAirCurrent += String.format(
                                                "%01x",
                                                *arrayOf<Any?>(cArr[2].code and 15)
                                            )
                                            DeviceControlActivity.strTAirSetting =
                                                String.format("%01x", *arrayOf<Any?>(cArr[4].code shr 4))
                                            DeviceControlActivity.strTAirSetting += String.format(
                                                "%01x",
                                                *arrayOf<Any?>(cArr[4].code and 15)
                                            )
                                            if (cArr[5].code == 1) {
                                                FragmentControlAircon.enableAirPwr = true
                                            } else {
                                                FragmentControlAircon.enableAirPwr = false
                                            }
                                            FragmentControlAircon.stepWind = cArr[6]
                                            if (this.displayIndex == R.integer.Display_CtlAircon) {
                                                this.fragmentControlAircon.airconDisplay()
                                            }
                                            if (this.stepSelfTest == 1) {
                                                this.stepSelfTest = 3
                                                this.strErrCode = str
                                            }
                                        } else {
                                            var str6 = str
                                            var b21: Byte = 0
                                            while (b21 < 6) {
                                                str6 = str6 + Integer.toHexString(cArr[b21.toInt()].code)
                                                b21 = (b21 + 1).toByte()
                                            }
                                            println("Rx(CMD2_REQ_STS_AIRC): " + str6)
                                            while (b < 4) {
                                                str = str + cArr[b + 2]
                                                b = (b + 1).toByte()
                                            }
                                            println("ErrCode=" + str)
                                            if (this.stepSelfTest == 0) {
                                                val dialogInterface2: DialogInterface? = this.mPopupSelfTest1
                                                if (dialogInterface2 != null) {
                                                    dialogInterface2.dismiss()
                                                }
                                                val builder2: AlertDialog.Builder = Builder(this)
                                                builder2.setTitle("[고장진단]" as CharSequence)
                                                if (str == "EL01") {
                                                    builder2.setMessage("에러코드(냉방): EL01 \nxxx제어기 통신에러 \n해당 디바이스의 점검이 필요합니다" as CharSequence)
                                                } else if (str == "BL01") {
                                                    builder2.setMessage("에러코드: BL01 \nxxxx 통신에러 \n해당 디바이스의 점검이 필요합니다" as CharSequence)
                                                } else {
                                                    builder2.setMessage(("에러코드: " as CharSequence).toString() + str + "\n정의되지않음 \n해당 디바이스의 점검이 필요합니다")
                                                }
                                                this.mPopupSelfTest1 = builder2.show()
                                            } else {
                                                this.stepSelfTest = 3
                                                this.strErrCode = str
                                            }
                                        }
                                        if (DeviceControlActivity.enableDevFan && !DeviceControlActivity.isCommInitAircon) {
                                            DeviceControlActivity.txDcommReqFanStatus = true
                                            DeviceControlActivity.isCommInitAircon = true
                                        }
                                        Log.d(
                                            DeviceControlActivity.TAG,
                                            "isAddTempAircon=" + DeviceControlActivity.isAddTempAircon + " enableDevTemp=" + DeviceControlActivity.enableDevTemp + " enableDevAircon=" + DeviceControlActivity.enableDevAircon
                                        )
                                        return
                                    }

                                    else -> when (b5) {
                                        115 -> {
                                            DeviceControlActivity.TxDcommSetWindFan = false
                                            println("TxDcommSetWindFan clear ")
                                            return
                                        }

                                        116 -> {
                                            DeviceControlActivity.TxDcommSetPwrFan = false
                                            println("TxDcommSetPwrFan clear ")
                                            return
                                        }

                                        117 -> {
                                            MakeTxFramePass(DeviceControlActivity.CMD2_REQ_STS_FAN, 1.toByte(), cArr)
                                            if (cArr[0].code == 0) {
                                                var b22: Byte = 0
                                                while (b22 < 6) {
                                                    str = str + Integer.toHexString(cArr[b22.toInt()].code)
                                                    b22 = (b22 + 1).toByte()
                                                }
                                                println("Rx(CMD2_REQ_STS_FAN): " + str)
                                                DeviceControlActivity.strFanTime =
                                                    String.format("%01x", *arrayOf<Any?>(cArr[3].code shr 4))
                                                DeviceControlActivity.strFanTime += String.format(
                                                    "%01x",
                                                    *arrayOf<Any?>(cArr[3].code and 15)
                                                )
                                                if (cArr[1].code == 1) {
                                                    FragmentControlFan.enableFanPwr = true
                                                } else {
                                                    FragmentControlFan.enableFanPwr = false
                                                }
                                                FragmentControlFan.stepFanWind = cArr[2]
                                                if (this.displayIndex == R.integer.Display_CtlFan) {
                                                    this.fragmentControlFan.fanDisplay()
                                                    return
                                                }
                                                return
                                            }
                                            return
                                        }

                                        118 -> {
                                            DeviceControlActivity.TxDcommSetTimeFan = false
                                            println("TxDcommSetTimeFan clear ")
                                            return
                                        }

                                        else -> when (b5) {
                                            124 -> {
                                                println("CMD2_REQ_ALIVE receive ")
                                                return
                                            }

                                            125 -> {
                                                DeviceControlActivity.txDcommReqSumPwr = false
                                                Log.d(
                                                    DeviceControlActivity.TAG,
                                                    "txDcommReqSumPwr clear step=" + Integer.toHexString(cArr[12].code)
                                                )
                                                val c2 = ((cArr[0].code shl 8) or cArr[1].code).toChar()
                                                val str7 = str + c2.code.toString() + " "
                                                val c3 = (cArr[3].code or (cArr[2].code shl 8)).toChar()
                                                val i3 = c2.code + c3.code
                                                val str8 = str7 + c3.code.toString() + " "
                                                val c4 = ((cArr[4].code shl 8) or cArr[5].code).toChar()
                                                val i4 = i3 + c4.code
                                                val str9 = str8 + c4.code.toString() + " "
                                                val c5 = ((cArr[6].code shl 8) or cArr[7].code).toChar()
                                                val i5 = i4 + c5.code
                                                val str10 = str9 + c5.code.toString() + " "
                                                val c6 = ((cArr[8].code shl 8) or cArr[9].code).toChar()
                                                val c7 = ((cArr[10].code shl 8) or cArr[11].code).toChar()
                                                val i6 = i5 + c6.code + c7.code
                                                val str11 =
                                                    (str10 + c6.code.toString() + " ") + c7.code.toString() + "\n"
                                                val c8 = cArr[12]
                                                if (c8.code == 0) {
                                                    this.str_watt = "01~06: " + str11
                                                    this.wattSum = i6
                                                } else if (c8.code == 1) {
                                                    this.str_watt += "07~12: " + str11
                                                    this.wattSum += i6
                                                } else if (c8.code == 2) {
                                                    this.str_watt += "13~18: " + str11
                                                    this.wattSum += i6
                                                } else if (c8.code == 3) {
                                                    this.str_watt += "19~24: " + str11
                                                    this.wattSum += i6
                                                    this.str_watt += "===> 총 누적 전력량: " + this.wattSum.toString() + "w"
                                                }
                                                if (cArr[12].code == 3) {
                                                    val builder3: AlertDialog.Builder = Builder(this)
                                                    builder3.setTitle("[전력량 모니터링]" as CharSequence)
                                                    builder3.setMessage(this.str_watt as CharSequence?)
                                                    builder3.show()
                                                    return
                                                }
                                                return
                                            }

                                            126 -> {
                                                println("txDcommReqSwitchSet clear ")
                                                DeviceControlActivity.txDcommReqSwitchSet = false
                                                if (FragmenSetupSwitch.isSetupFirst) {
                                                    var b23: Byte = 0
                                                    while (b23 < 10) {
                                                        str = str + Integer.toHexString(cArr[b23.toInt()].code)
                                                        b23 = (b23 + 1).toByte()
                                                    }
                                                    println("Rx(CMD2_REQ_SETTING): " + str)
                                                    FragmenSetupSwitch.setup_id = cArr[0]
                                                    FragmenSetupSwitch.setup_id2 = cArr[1]
                                                    FragmenSetupSwitch.setup_lamp = cArr[2]
                                                    FragmenSetupSwitch.setup_concent = cArr[3]
                                                    FragmenSetupSwitch.setup_wallpad = cArr[4]
                                                    FragmenSetupSwitch.setup_way3 = cArr[5]
                                                    FragmenSetupSwitch.setup_pattern = cArr[6]
                                                    FragmenSetupSwitch.setup_relay = cArr[7]
                                                    FragmenSetupSwitch.setup_touch = cArr[8]
                                                    FragmenSetupSwitch.setup_ct = cArr[9]
                                                    onFragmentChanged(R.integer.Display_SetupSwitch)
                                                    return
                                                }
                                                if (FragmenSetupSwitch.setup_lamp !== this.lampCount.code - '0'.code) {
                                                    this.lampCount = (FragmenSetupSwitch.setup_lamp + 48) as Char
                                                }
                                                onFragmentChanged(R.integer.Display_CtlMain)
                                                Toast.makeText(this, "설정 변경되었습니다.", 0).show()
                                                return
                                            }

                                            Byte.Companion.MAX_VALUE -> {
                                                println("txDcommReqSelfTest clear ")
                                                this.txDcommReqSelfTest = false
                                                return
                                            }

                                            else -> return
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    DeviceControlActivity.txDcommReqFanStatus = false
                    println("txDcommReqFanStatus clear ")
                }
            } else {
                DeviceControlActivity.txDcommReqWatt = false
                println("3)txDcommReqWatt clear ")
            }
        } else {
            println("!!!!Rcv Data CS Error")
        }
    } else {
        println("!!!!Rcv Data Err")
    }
}**/