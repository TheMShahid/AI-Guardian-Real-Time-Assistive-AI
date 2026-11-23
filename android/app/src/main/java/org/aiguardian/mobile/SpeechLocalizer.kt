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
                "front-left" -> "سامنے بائیں طرف"
                "front-right" -> "سامنے دائیں طرف"
                "left" -> "آپ کے بائیں جانب"
                "right" -> "آپ کے دائیں جانب"
                else -> "بالکل سامنے"
            }
            "Pashto (Afghanistan)" -> when (direction) {
                "front-left" -> "مخکې لږ چپ لوري ته"
                "front-right" -> "مخکې لږ ښي لوري ته"
                "left" -> "ستاسو چپ لوري ته"
                "right" -> "ستاسو ښي لوري ته"
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
        val normalizedLabel = label.trim().lowercase()
        return when (language) {
            "Urdu (Pakistan)" -> {
                val name = urduLabels[normalizedLabel] ?: label
                "${directionPhrase(direction, language)} $name موجود ہے۔"
            }
            "Pashto (Afghanistan)" -> {
                val name = pashtoLabels[normalizedLabel] ?: label
                "${directionPhrase(direction, language)} $name شتون لري."
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
        val name = when (language) {
            "Urdu (Pakistan)" -> urduLabels[label.trim().lowercase()] ?: label
            "Pashto (Afghanistan)" -> pashtoLabels[label.trim().lowercase()] ?: label
            else -> label
        }
        val proximity = when (apparentProximity) {
            "very-close-looking" -> when (language) {
                "Urdu (Pakistan)" -> "احتیاط، کیمرے میں $name کا حصہ بہت بڑا نظر آ رہا ہے۔"
                "Pashto (Afghanistan)" -> "احتیاط، د $name لویه برخه په کمره کې ښکاري."
                else -> "Caution. A large part of the $name is visible in the camera view."
            }
            "close-looking" -> when (language) {
                "Urdu (Pakistan)" -> "کیمرے میں $name کا حصہ نسبتاً بڑا نظر آ رہا ہے۔"
                "Pashto (Afghanistan)" -> "د $name لویه برخه په کمره کې ښکاري."
                else -> "A relatively large part of the $name is visible in the camera view."
            }
            else -> when (language) {
                "Urdu (Pakistan)" -> "کیمرے میں $name کا حصہ نسبتاً چھوٹا نظر آ رہا ہے۔"
                "Pashto (Afghanistan)" -> "د $name کوچنۍ برخه په کمره کې ښکاري."
                else -> "A relatively small part of the $name is visible in the camera view."
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
