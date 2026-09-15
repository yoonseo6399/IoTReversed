package io.github.yoonseo6399.communication

/**
 * APK MakeTxFrame/rxAnalyzer ASCII protocol catalog. NOT a binary Packet command.
 * For example 0x41 controls a lamp here, but reports lamp status in binary pass-through.
 * CMD2 temperature/fan extensions also appear explicitly in MakeTxFramePass; those
 * supported binary forms live in Command.Temp/Air/Fan. This catalog alone is not a transport.
 */
enum class Cmd2Command(val code: Int) {
    CONTROL_LAMP(65), CONTROL_OUTLET(66), REQUEST_POWER(67), DEVICE_STATUS(68), SET_INFO(69),
    SET_FUNCTION(70), SET_WAKE(71), SET_SLEEP(72), SET_PATTERN(73), SET_SECURITY(74), TIME_INFO(75), SET_WATCH(76),
    LAMP_STATUS(80), OUTLET_STATUS(81), OUTLET_POWER(82), OUTLET_CUTOFF(83), POWER_STATUS(84), TIME1(87), TIME2(88),
    REQUEST_TEMPERATURE(97), TEMPERATURE_ETC(98), SET_TEMPERATURE(99), SET_AWAY(100), TEMPERATURE_STATUS(101),
    REQUEST_AIR(102), AIR_ETC(103), SET_AIR_TEMPERATURE(104), SET_AIR_POWER(105), SET_AIR_WIND(106), AIR_STATUS(107),
    DUMMY(111), OTHER_DEVICES(112), REQUEST_FAN(113), FAN_ETC(114), SET_FAN_WIND(115), SET_FAN_POWER(116),
    FAN_STATUS(117), SET_FAN_TIME(118), TEST(121), TO_JUNG(122), FROM_JUNG(123), ALIVE(124), SETTING(126), SELF_TEST(127);

    companion object {
        fun fromCode(code: Int): Cmd2Command? = entries.firstOrNull { it.code == code }
    }
}
