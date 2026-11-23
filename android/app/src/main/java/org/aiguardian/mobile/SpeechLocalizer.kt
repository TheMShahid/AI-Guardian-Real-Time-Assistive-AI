package org.aiguardian.mobile

/**
 * Natural spoken guidance from detector labels and visual cues.
 * Apparent proximity is not calibrated physical distance.
 */
object SpeechLocalizer {
    private val urduLabels = mapOf(
        "person" to "شخص", "bicycle" to "سائیکل", "car" to "گاڑی",
        "motorcycle" to "موٹر سائیکل", "airplane" to "ہوائی جہاز",
        "bus" to "بس", "train" to "ٹرین", "truck" to "ٹرک",
        "boat" to "کشتی", "traffic light" to "ٹریفک سگنل",
        "fire hydrant" to "فائر ہائیڈرنٹ", "stop sign" to "اسٹاپ سائن",
        "parking meter" to "پارکنگ میٹر", "bench" to "بینچ",
        "bird" to "پرندہ", "cat" to "بلی", "dog" to "کتا",
        "horse" to "گھوڑا", "sheep" to "بھیڑ", "cow" to "گائے",
        "elephant" to "ہاتھی", "bear" to "ریچھ", "zebra" to "زیبرا",
        "giraffe" to "زرافہ", "backpack" to "بیگ", "umbrella" to "چھتری",
        "handbag" to "ہینڈ بیگ", "tie" to "ٹائی", "suitcase" to "سوٹ کیس",
        "frisbee" to "فریسبی", "skis" to "اسکیز", "snowboard" to "سنوبورڈ",
        "sports ball" to "گیند", "kite" to "پتنگ", "baseball bat" to "بیس بال بیٹ",
        "baseball glove" to "بیس بال گلَو", "skateboard" to "اسکیٹ بورڈ",
        "surfboard" to "سرف بورڈ", "tennis racket" to "ٹینس ریکیٹ",
        "bottle" to "بوتل", "wine glass" to "گلاس", "cup" to "کپ",
        "fork" to "کانٹا", "knife" to "چھری", "spoon" to "چمچ",
        "bowl" to "پیالہ", "banana" to "کیلا", "apple" to "سیب",
        "sandwich" to "سینڈوچ", "orange" to "سنگترہ", "broccoli" to "بروکلی",
        "carrot" to "گاجر", "hot dog" to "ہاٹ ڈاگ", "pizza" to "پیزا",
        "donut" to "ڈونٹ", "cake" to "کیک", "chair" to "کرسی",
        "couch" to "صوفہ", "potted plant" to "گملے والا پودا",
        "bed" to "بستر", "dining table" to "میز", "toilet" to "ٹوائلٹ",
        "tv" to "ٹیلی ویژن", "laptop" to "لیپ ٹاپ", "mouse" to "کمپیوٹر ماؤس",
        "remote" to "ریموٹ", "keyboard" to "کی بورڈ", "cell phone" to "موبائل فون",
        "microwave" to "مائیکرو ویو", "oven" to "اوون", "toaster" to "ٹوسٹر",
        "sink" to "سنک", "refrigerator" to "فریج", "book" to "کتاب",
        "clock" to "گھڑی", "vase" to "گلدان", "scissors" to "قینچی",
        "teddy bear" to "ٹیڈی بیئر", "hair drier" to "ہیئر ڈرائر",
        "toothbrush" to "دانتوں کا برش"
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
                "front-left" -> "آپ کے سامنے، تھوڑا بائیں طرف"
                "front-right" -> "آپ کے سامنے، تھوڑا دائیں طرف"
                "left" -> "آپ کے بائیں ہاتھ پر"
                "right" -> "آپ کے دائیں ہاتھ پر"
                else -> "بالکل آپ کے سامنے"
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
                "آپ کے ${directionPhrase(direction, language)} ایک $name موجود ہے۔"
            }
            "Pashto (Afghanistan)" -> {
                val name = pashtoLabels[normalizedLabel] ?: label
                "ستاسو ${directionPhrase(direction, language)} $name شتون لري."
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
                "Urdu (Pakistan)" -> "احتیاط، یہ چیز کیمرے میں بہت قریب دکھائی دے رہی ہے۔"
                "Pashto (Afghanistan)" -> "احتیاط، دا په کمره کې ډېره نږدې ښکاري."
                else -> "Caution. It looks very close in the camera view."
            }
            "close-looking" -> when (language) {
                "Urdu (Pakistan)" -> "یہ چیز قریب دکھائی دے رہی ہے۔"
                "Pashto (Afghanistan)" -> "دا نږدې ښکاري."
                else -> "It looks fairly close."
            }
            else -> when (language) {
                "Urdu (Pakistan)" -> "یہ چیز کیمرے میں نسبتاً دور دکھائی دے رہی ہے۔"
                "Pashto (Afghanistan)" -> "دا په کمره کې لږ لیرې ښکاري."
                else -> "It looks farther away in the camera view."
            }
        }
        return "$proximity $base"
    }

    fun localizeUnobservedSector(sector: String, language: String): String {
        return when (language) {
            "Urdu (Pakistan)" -> when (sector) {
                "left" -> "کیمرے میں بائیں طرف کوئی پہچانی ہوئی رکاوٹ نظر نہیں آ رہی۔"
                "right" -> "کیمرے میں دائیں طرف کوئی پہچانی ہوئی رکاوٹ نظر نہیں آ رہی۔"
                else -> "کیمرے میں آگے کوئی پہچانی ہوئی رکاوٹ نظر نہیں آ رہی۔"
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
