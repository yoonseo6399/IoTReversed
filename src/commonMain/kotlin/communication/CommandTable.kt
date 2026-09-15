package io.github.yoonseo6399.communication


//그래서 tx 요청 -> Rx -> tx ( 나 데이터 받았어요 ) ㅇㅋ
/** Binary pass-through commands (7e/20/0f). ASCII CMD2 has a separate catalog. */
sealed class Command(val byte: Byte, val description: String,open val ack : Boolean = true) {

    override fun equals(other: Any?): Boolean {
        return other is Command && other.byte == byte
    }
    override fun hashCode(): Int = byte.hashCode()

    open fun parse(data: ByteArray): Any = data
    // --- 전등 관련 ---
    sealed class Lamp(byte: Byte, description: String,ack : Boolean = true) : Command(byte, description,ack) {
        object Control : Lamp(49, "전등 제어 요청",false)
        object State : Lamp(65, "전등 상태 요청"){
            override fun parse(data: ByteArray): List<Boolean> {

                return data.drop(1).take(data.first().toInt()).map { it == 1.toByte() }
            }
        }
    }

    // --- 콘센트 관련 ---
    sealed class Conc(byte: Byte, description: String,ack : Boolean = true) : Command(byte, description,ack) {
        object Control : Conc(50, "콘센트 제어 요청",false)
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
                // Existing firmware inference, not decoded by the APK. Keep count/padding bounded.
                require(data.isNotEmpty()) { "Missing outlet count" }
                val count = data[0].toInt() and 255
                require(data.size >= 1 + count * 2) { "Truncated outlet power payload" }
                return List(count) { index ->
                    val offset = 1 + index * 2
                    (((data[offset].toInt() and 255) shl 8) or
                        (data[offset + 1].toInt() and 255)) / 2.0
                }
            }
        }
        object CutState : Conc(68, "콘센트 대기전력차단 상태 요청")
    }

    // --- 전원/일괄제어 관련 ---
    sealed class Power(byte: Byte, description: String) : Command(byte, description) {
        object Control : Power(51, "소비전력 읽기 요청") { override val ack = false }
        object State : Power(69, "소비전력 상태") {
            override fun parse(data: ByteArray): Int = parsePowerValue(data)
        }
        object TotalPower : Power(125, "누적 전력량 페이지") {
            override fun parse(data: ByteArray) = parseEnergyPage(data)
        }
    }
    // --- 기기/시스템 관련 ---
    sealed class Device(byte: Byte, description: String,ack : Boolean = true) : Command(byte, description,ack) {
        object Status : Device(52, "기기 전체 상태 요청",false)
        object OtherDevices : Device(112, "추가 기기 정보", false) {
            override fun parse(data: ByteArray) = parseCapabilities(data)
        }
        object SetInfo : Device(53, "설정 정보 요청")
        object TimeInfo : Device(62, "시간 정보 요청")
        object Alive : Device(124, "생존 확인(ALIVE)")
        //object DetailStatus : Device(68, "기기 상태 상세(CMD2)")
    }

    // --- 냉난방/온도 관련 ---
    sealed class Temp(byte: Byte, description: String) : Command(byte, description) {
        object RawState : Temp(97, "온도 상태 요청(CMD2)") { override val ack = false }
        object Etc : Temp(98, "온도 기타 설정(CMD2)")
        object ControlChange : Temp(99, "온도 변경 제어(CMD2)") { override val ack = false }
        object ControlExit : Temp(100, "외출 온도 제어(CMD2)") { override val ack = false }
        object DetailState : Temp(101, "온도 상태 상세(CMD2)") {
            override fun parse(data: ByteArray) = parseTemperature(data)
        }
    }

    // --- 에어컨 관련 ---
    sealed class Air(byte: Byte, description: String) : Command(byte, description) {
        object RawState : Air(102, "에어컨 상태 요청(CMD2)") { override val ack = false }
        object ControlTemperature : Air(104, "에어컨 설정 온도 변경(CMD2)") { override val ack = false }
        object ControlPower : Air(105, "에어컨 전원 제어(CMD2)") { override val ack = false }
        object ControlWind : Air(106, "에어컨 풍량 제어(CMD2)") { override val ack = false }
        object DetailState : Air(107, "에어컨 상태 상세(CMD2)") {
            override fun parse(data: ByteArray) = parseAir(data)
        }
    }

    // --- 환기/팬 관련 ---
    sealed class Fan(byte: Byte, description: String) : Command(byte, description) {
        object RawState : Fan(113, "환기 상태 요청(CMD2)") { override val ack = false }
        object ControlWind : Fan(115, "환기 풍량 제어(CMD2)") { override val ack = false }
        object ControlPower : Fan(116, "환기 전원 제어(CMD2)") { override val ack = false }
        object DetailState : Fan(117, "환기 상태 상세(CMD2)") {
            override fun parse(data: ByteArray) = parseFan(data)
        }
        object ControlTime : Fan(118, "환기 타이머(CMD2)") { override val ack = false }
    }

    // --- 모드/설정 관련 ---
    sealed class Mode(byte: Byte, description: String) : Command(byte, description) {
        object Wake : Mode(59, "기상 설정")
        object Sleep : Mode(60, "취침 설정")
        object Watch : Mode(63, "시계/버전 설정")
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
