package org.aiguardian.mobile

/**
 * Natural spoken guidance from detected object labels and visual cues.
 * Apparent proximity is not calibrated physical distance.
 */
object SpeechLocalizer {
    private val urduLabels = mapOf(
        "person" to "insaan", "bicycle" to "cycle", "car" to "gaari",
        "motorcycle" to "motorcycle", "airplane" to "hawai jahaz",
        "bus" to "bus", "train" to "train", "truck" to "truck",
        "boat" to "kashti", "traffic light" to "traffic signal",
        "fire hydrant" to "fire hydrant", "stop sign" to "stop sign",
        "parking meter" to "parking meter", "bench" to "bench",
        "bird" to "parinda", "cat" to "billi", "dog" to "kutta",
        "horse" to "ghora", "sheep" to "bher", "cow" to "gaaye",
        "elephant" to "haathi", "bear" to "reech", "zebra" to "zebra",
        "giraffe" to "giraffe", "backpack" to "bag", "umbrella" to "chhatri",
        "handbag" to "handbag", "tie" to "tie", "suitcase" to "suitcase",
        "frisbee" to "frisbee", "skis" to "skis", "snowboard" to "snowboard",
        "sports ball" to "ball", "kite" to "patang", "baseball bat" to "bat",
        "baseball glove" to "glove", "skateboard" to "skateboard",
        "surfboard" to "surfboard", "tennis racket" to "tennis racket",
        "bottle" to "bottle", "wine glass" to "glass", "cup" to "cup",
        "fork" to "kaanta", "knife" to "chhuri", "spoon" to "chamach",
        "bowl" to "pyali", "banana" to "kela", "apple" to "seb",
        "sandwich" to "sandwich", "orange" to "malta", "broccoli" to "broccoli",
        "carrot" to "gajar", "hot dog" to "hot dog", "pizza" to "pizza",
        "donut" to "donut", "cake" to "cake", "chair" to "kursi",
        "couch" to "sofa", "potted plant" to "gamlay wala poda",
        "bed" to "bistar", "dining table" to "mez", "toilet" to "toilet",
        "tv" to "television", "laptop" to "laptop", "mouse" to "computer mouse",
        "remote" to "remote", "keyboard" to "keyboard", "cell phone" to "mobile phone",
        "microwave" to "microwave", "oven" to "oven", "toaster" to "toaster",
        "sink" to "sink", "refrigerator" to "fridge", "book" to "kitaab",
        "clock" to "ghari", "vase" to "guldaan", "scissors" to "qainchi",
        "teddy bear" to "teddy bear", "hair drier" to "hair dryer",
        "toothbrush" to "toothbrush"
    )

    private val pashtoLabels = mapOf(
        "person" to "شخص", "bicycle" to "بایسکل", "car" to "موټر",
        "motorcycle" to "موټرسایکل", "airplane" to "الوتکه", "bus" to "بس",
        "train" to "اورګاډی", "truck" to "لارۍ", "boat" to "کښتۍ",
        "bird" to "مرغه", "cat" to "پیشو", "dog" to "سپی",
        "horse" to "آس", "backpack" to "بکس", "umbrella" to "چترۍ",
        "bottle" to "بوتل", "cup" to "پیاله", "banana" to "کېله",
        "apple" to "مڼه", "chair" to "څوکۍ", "couch" to "سوفه",
        "bed" to "کټ", "dining table" to "د ډوډۍ مېز",
        "toilet" to "تشناب", "tv" to "تلویزیون", "laptop" to "لېپټاپ",
        "cell phone" to "ګرځنده ټیلیفون", "book" to "کتاب", "clock" to "ساعت"
    )

    private fun directionPhrase(direction: String, language: String): String {
        return when (language) {
            "Urdu (Pakistan)" -> when (direction) {
                "front-left" -> "aapke saamne, thora bayen taraf"
                "front-right" -> "aapke saamne, thora dayen taraf"
                "left" -> "aapke bayen haath par"
                "right" -> "aapke dayen haath par"
                else -> "bilkul aapke saamne"
            }
            "Pashto (Afghanistan)" -> when (direction) {
                "front-left" -> "ستاسو مخې ته، لږ چپ لوري ته"
                "front-right" -> "ستاسو مخې ته، لږ ښي لوري ته"
                "left" -> "ستاسو چپ لاس ته"
                "right" -> "ستاسو ښي لاس ته"
                else -> "ستاسو مخې ته"
            }
            else -> when (direction) {
                "front-left" -> "ahead of you, slightly to your left"
                "front-right" -> "ahead of you, slightly to your right"
                "left" -> "on your left"
                "right" -> "on your right"
                else -> "directly ahead of you"
            }
        }
    }

    fun localize(label: String, direction: String, language: String): String {
        val normalizedLabel = label.lowercase()
        return when (language) {
            "Urdu (Pakistan)" -> {
                val name = urduLabels[normalizedLabel] ?: normalizedLabel
                "Aapke ${directionPhrase(direction, language)} ek $name hai."
            }
            "Pashto (Afghanistan)" -> {
                val name = pashtoLabels[normalizedLabel] ?: label
                "ستاسو ${directionPhrase(direction, language)} $name شته."
            }
            else -> "There is a $label ${directionPhrase(direction, language)}."
        }
    }

    fun localizeObstacle(
        label: String,
        direction: String,
        apparentProximity: String,
        language: String
    ): String {
        val base = localize(label, direction, language)
        val proximity = when (apparentProximity) {
            "very-close-looking" -> when (language) {
                "Urdu (Pakistan)" -> "Ehtiyat, yeh camera mein bohat qareeb nazar aa rahi hai."
                "Pashto (Afghanistan)" -> "احتیاط، دا په کمره کې ډېره نږدې ښکاري."
                else -> "Caution. It looks very close in the camera view."
            }
            "close-looking" -> when (language) {
                "Urdu (Pakistan)" -> "Yeh qareeb lag rahi hai."
                "Pashto (Afghanistan)" -> "دا نږدې ښکاري."
                else -> "It looks fairly close."
            }
            else -> when (language) {
                "Urdu (Pakistan)" -> "Yeh camera mein kuch door nazar aa rahi hai."
                "Pashto (Afghanistan)" -> "دا په کمره کې لږ لیرې ښکاري."
                else -> "It looks farther away in the camera view."
            }
        }
        return "$proximity $base"
    }

    fun localizeUnobservedSector(sector: String, language: String): String {
        return when (language) {
            "Urdu (Pakistan)" -> when (sector) {
                "left" -> "Camera mein bayen taraf koi pehchani hui rukawat nazar nahi aa rahi."
                "right" -> "Camera mein dayen taraf koi pehchani hui rukawat nazar nahi aa rahi."
                else -> "Camera mein aage koi pehchani hui rukawat nazar nahi aa rahi."
            }
            "Pashto (Afghanistan)" -> when (sector) {
                "left" -> "د کمرې په چپ لوري کې پېژندل شوی خنډ نه ښکاري."
                "right" -> "د کمرې په ښي لوري کې پېژندل شوی خنډ نه ښکاري."
                else -> "د کمرې مخې ته پېژندل شوی خنډ نه ښکاري."
            }
            else -> when (sector) {
                "left" -> "No recognized obstacle is visible on your left in the camera view."
                "right" -> "No recognized obstacle is visible on your right in the camera view."
                else -> "No recognized obstacle is visible ahead in the camera view."
            }
        }
    }
}
