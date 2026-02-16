package io.github.yoonseo6399.communication


//그래서 tx 요청 -> Rx -> tx ( 나 데이터 받았어요 ) ㅇㅋ
sealed class Command(val byte: Byte, val description: String) {

    override fun equals(other: Any?): Boolean {
        return other is Command && other.byte == byte
    }
    open fun parse(data: ByteArray): Any = data
    // --- 전등 관련 ---
    sealed class Lamp(byte: Byte, description: String) : Command(byte, description) {
        object Control : Lamp(49, "전등 제어 요청")
        object State : Lamp(65, "전등 상태 요청"){
            override fun parse(data: ByteArray): List<Boolean> {

                return data.drop(1).take(data.first().toInt()).map { it == 1.toByte() }
            }
        }
        object DetailState : Lamp(80, "전등 상태 상세(CMD2)")
    }

    // --- 콘센트 관련 ---
    sealed class Conc(byte: Byte, description: String) : Command(byte, description) {
        object Control : Conc(50, "콘센트 제어 요청")
        object State : Conc(66, "콘센트 상태 요청"){
            override fun parse(data: ByteArray): List<Boolean> {
                val count = data.first().toInt()
                val activeStatus = data.slice(1..count).map { it == 1.toByte() }
                val restIDK = data.drop(1+count) //TODO find what that means
                return activeStatus
            }
        }
        object PowerState : Conc(67, "콘센트 소비전력 요청"){
            override fun parse(data: ByteArray): List<Double> {
                val count = data.first().toInt()
                val wattages = data.drop(1).chunked(2).map {
                    val hi = it[0].toInt() and 0xFF
                    val lo = it[1].toInt() and 0xFF
                    val rawValue = (hi shl 8) or lo
                    return@map rawValue / 2.0
                }
                return wattages
            }
        }
        object CutState : Conc(68, "콘센트 대기전력차단 상태 요청")
        object DetailState : Conc(81, "콘센트 상태 상세(CMD2)")
    }

    // --- 전원/일괄제어 관련 ---
    sealed class Power(byte: Byte, description: String) : Command(byte, description) {
        object Control : Power(51, "일괄제어 요청")
        object State : Power(69, "일괄제어 상태 요청")
        object TotalPower : Power(125, "합산 소비전력 요청")
        object DetailState : Power(84, "일괄제어 상태 상세(CMD2)")
    }
    // --- 기기/시스템 관련 ---
    sealed class Device(byte: Byte, description: String) : Command(byte, description) {
        object Status : Device(52, "기기 전체 상태 요청")
        object SetInfo : Device(53, "설정 정보 요청")
        object TimeInfo : Device(62, "시간 정보 요청")
        object Alive : Device(124, "생존 확인(ALIVE)")
        //object DetailStatus : Device(68, "기기 상태 상세(CMD2)")
    }

    // --- 냉난방/온도 관련 ---
    sealed class Temp(byte: Byte, description: String) : Command(byte, description) {
        object RawState : Temp(97, "온도 상태 수신(CMD2)")
        object Etc : Temp(98, "온도 기타 설정(CMD2)")
        object ControlChange : Temp(99, "온도 변경 제어(CMD2)")
        object ControlExit : Temp(100, "외출 온도 제어(CMD2)")
        object DetailState : Temp(101, "온도 상태 상세(CMD2)")
    }

    // --- 에어컨 관련 ---
    sealed class Air(byte: Byte, description: String) : Command(byte, description) {
        object RawState : Air(102, "에어컨 상태 수신(CMD2)")
        object ControlMode : Air(104, "에어컨 모드 변경(CMD2)")
        object ControlPower : Air(105, "에어컨 전원 제어(CMD2)")
        object ControlWind : Air(106, "에어컨 풍량 제어(CMD2)")
        object DetailState : Air(107, "에어컨 상태 상세(CMD2)")
    }

    // --- 환기/팬 관련 ---
    sealed class Fan(byte: Byte, description: String) : Command(byte, description) {
        object RawState : Fan(113, "환기 상태 수신(CMD2)")
        object ControlWind : Fan(115, "환기 풍량 제어(CMD2)")
        object ControlPower : Fan(116, "환기 전원 제어(CMD2)")
        object DetailState : Fan(117, "환기 상태 상세(CMD2)")
    }

    // --- 모드/설정 관련 ---
    sealed class Mode(byte: Byte, description: String) : Command(byte, description) {
        object Wake : Mode(59, "기상 설정")
        object Sleep : Mode(60, "취침 설정")
        object Out : Mode(63, "외출 설정")
        object Pattern : Mode(54, "패턴 제어")
        object Setting : Mode(126, "기기 세팅(CMD2)")
    }

    override fun toString(): String {
        return "$byte($description)"
    }

    // --- 동적 조회를 위한 Companion Object ---
    companion object {
        internal val allCommands: List<Command> by lazy {
            Command::class.nestedClasses.toMutableList().flatMap { it.nestedClasses }.map { it.objectInstance as Command }
        }

        fun fromByte(byte: Byte): Command? = allCommands.find { it.byte == byte }

        fun getName(byte: Byte): String {
            val cmd = fromByte(byte) ?: return "UNKNOWN(0x${"%02X".format(byte)})"
            // 클래스명을 계층적으로 표시 (예: Lamp.Control)
            return "${cmd::class.java.enclosingClass.simpleName}.${cmd::class.java.simpleName}"
        }
    }
}

//fun a (){
//
//    for (byte b15 = 0; b15 < 5; b15 = (byte)(b15 + 1)) {
//        str = str + Integer.toHexString(cArr[b15]);
//    }
//    Log.d(TAG, "Rx(CMD2_REQ_STS_LAMP): " + str);
//    this.lampCount = (char)(cArr[0] + '0');
//    byte b16 = 0;
//    while (b16 < 4) {
//        int i2 = b16 +1;
//        this.lampState[b16] = (char)(cArr[i2] + '0');
//        b16 = (byte) i2;
//    }
//}