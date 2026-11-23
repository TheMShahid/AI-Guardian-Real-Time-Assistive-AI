package org.aiguardian.mobile

/**
 * Natural spoken guidance from detector labels and visual cues.
 * Proximity is an uncalibrated visual estimate, never a physical distance.
 * An unobserved camera sector is not proof of a clear or safe walking path.
 */
object SpeechLocalizer {
    private val urduLabels = mapOf(
        "person" to "شخص", "bicycle" to "سائیکل", "car" to "گاڑی",
        "motorcycle" to "موٹر سائیکل", "airplane" to "ہوائی جہاز",
        "bus" to "بس", "train" to "ٹرین", "truck" to "ٹرک",
        "boat" to "کشتی", "traffic light" to "ٹریفک سگنل",
        "fire hydrant" to "فائر ہائیڈرینٹ", "stop sign" to "اسٹاپ سائن",
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
                "front-left" -> "آپ کے آگے بائیں جانب"
                "front-right" -> "آپ کے آگے دائیں جانب"
                "left" -> "آپ کے بائیں جانب"
                "right" -> "آپ کے دائیں جانب"
                else -> "آپ کے عین سامنے"
            }
            "Pashto (Afghanistan)" -> when (direction) {
                "front-left" -> "ستاسو مخې ته چپ لوري ته"
                "front-right" -> "ستاسو مخې ته ښي لوري ته"
                "left" -> "ستاسو چپ لوري ته"
                "right" -> "ستاسو ښي لوري ته"
                else -> "ستاسو مخې ته"
            }
            else -> when (direction) {
                "front-left" -> "ahead and to your left"
                "front-right" -> "ahead and to your right"
                "left" -> "on your left"
                "right" -> "on your right"
                else -> "straight ahead"
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
        language: String,
        appearsToApproach: Boolean = false
    ): String {
        val normalizedLabel = label.trim().lowercase()
        val name = when (language) {
            "Urdu (Pakistan)" -> urduLabels[normalizedLabel] ?: label
            "Pashto (Afghanistan)" -> pashtoLabels[normalizedLabel] ?: label
            else -> label
        }
        val directionText = directionPhrase(direction, language)

        if (appearsToApproach) {
            return when (language) {
                "Urdu (Pakistan)" ->
                    "توجہ دیں! $directionText $name کا سائز بڑھتا دکھائی دے رہا ہے، یعنی یہ قریب آتا ہوا ہو سکتا ہے۔ براہِ کرم رک جائیں اور راستہ خود چیک کریں۔"
                "Pashto (Afghanistan)" ->
                    "پام وکړئ! $name ستاسو $directionText لوی ښکاري او ښايي رانږدې کېږي. مهرباني وکړئ ودرېږئ او لاره وګورئ."
                else ->
                    "Attention. The $name appears to be getting closer, $directionText. Please stop and check your path."
            }
        }

        return when (apparentProximity) {
            "very-close-looking" -> when (language) {
                "Urdu (Pakistan)" ->
                    "احتیاط! $directionText $name قریب ہے۔ براہِ کرم رکیں اور آگے بڑھنے سے پہلے راستہ دیکھ لیں۔"
                "Pashto (Afghanistan)" ->
                    "احتیاط، $name ستاسو $directionText ډېر نږدې ښکاري. کره واټن نه دی معلوم. مهرباني وکړئ ودرېږئ او لاره وګورئ."
                else ->
                    "Caution. The $name appears very close, $directionText. Its exact distance is unknown. Please stop and check the way ahead."
            }
            "close-looking" -> when (language) {
                "Urdu (Pakistan)" ->
                    "$directionText $name ہے۔ یہ قریب دکھائی دے رہی ہے؛ آگے بڑھنے سے پہلے احتیاط کریں۔"
                "Pashto (Afghanistan)" ->
                    "$name ستاسو $directionText ښکاري. داسې برېښي چې نږدې دی، خو کره واټن نه دی معلوم. مخکې له تګ څخه لاره وګورئ."
                else ->
                    "The $name is $directionText. It appears relatively close, but its exact distance is unknown. Check the path before moving."
            }
            else -> when (language) {
                "Urdu (Pakistan)" ->
                    "$directionText $name نظر آ رہی ہے۔ یہ نسبتاً دور دکھائی دیتی ہے۔ درست فاصلہ معلوم نہیں۔"
                "Pashto (Afghanistan)" ->
                    "$name ستاسو $directionText ښکاري. داسې برېښي چې لږ لرې دی؛ کمره کره واټن نه شي معلومولی."
                else ->
                    "The $name is visible $directionText. It appears farther away; the camera cannot measure its exact distance."
            }
        }
    }

    fun localizeUnobservedSector(sector: String, language: String): String {
        return when (language) {
            "Urdu (Pakistan)" -> when (sector) {
                "left" -> "بائیں جانب کوئی واضح رکاوٹ نظر نہیں آ رہی۔ پھر بھی آگے بڑھنے سے پہلے راستہ چیک کریں۔"
                "right" -> "دائیں جانب کوئی واضح رکاوٹ نظر نہیں آ رہی۔ پھر بھی آگے بڑھنے سے پہلے راستہ چیک کریں۔"
                else -> "سامنے کوئی واضح رکاوٹ نظر نہیں آ رہی۔ پھر بھی آگے بڑھنے سے پہلے راستہ چیک کریں۔"
            }
            "Pashto (Afghanistan)" -> when (sector) {
                "left" -> "چپ لوري ته څرګند خنډ نه ښکاري. بیا هم مخکې له تګ څخه لاره وګورئ."
                "right" -> "ښي لوري ته څرګند خنډ نه ښکاري. بیا هم مخکې له تګ څخه لاره وګورئ."
                else -> "مخې ته څرګند خنډ نه ښکاري. بیا هم مخکې له تګ څخه لاره وګورئ."
            }
            else -> when (sector) {
                "left" -> "I don't see a clear obstacle to your left. Please still check the way before moving."
                "right" -> "I don't see a clear obstacle to your right. Please still check the way before moving."
                else -> "I don't see a clear obstacle ahead. Please still check the way before moving."
            }
        }
    }
}
