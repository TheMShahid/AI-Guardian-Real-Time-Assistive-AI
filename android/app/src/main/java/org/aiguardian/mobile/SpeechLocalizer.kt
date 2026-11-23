package org.aiguardian.mobile

/**
 * Converts detector labels and spatial directions into complete,
 * concise spoken guidance for the selected language.
 */
object SpeechLocalizer {
    private val urduLabels = mapOf(
        "person" to "شخص",
        "bicycle" to "سائیکل",
        "car" to "گاڑی",
        "motorcycle" to "موٹر سائیکل",
        "airplane" to "ہوائی جہاز",
        "bus" to "بس",
        "train" to "ٹرین",
        "truck" to "ٹرک",
        "boat" to "کشتی",
        "traffic light" to "ٹریفک سگنل",
        "bird" to "پرندہ",
        "cat" to "بلی",
        "dog" to "کتا",
        "horse" to "گھوڑا",
        "backpack" to "بیگ",
        "umbrella" to "چھتری",
        "bottle" to "بوتل",
        "cup" to "کپ",
        "banana" to "کیلا",
        "apple" to "سیب",
        "chair" to "کرسی",
        "couch" to "صوفہ",
        "bed" to "بستر",
        "dining table" to "کھانے کی میز",
        "toilet" to "ٹوائلٹ",
        "tv" to "ٹیلی ویژن",
        "laptop" to "لیپ ٹاپ",
        "cell phone" to "موبائل فون",
        "book" to "کتاب",
        "clock" to "گھڑی"
    )

    private val pashtoLabels = mapOf(
        "person" to "شخص",
        "bicycle" to "بایسکل",
        "car" to "موټر",
        "motorcycle" to "موټرسایکل",
        "airplane" to "الوتکه",
        "bus" to "بس",
        "train" to "اورګاډی",
        "truck" to "لارۍ",
        "boat" to "کښتۍ",
        "traffic light" to "ترافیکي څراغ",
        "bird" to "مرغه",
        "cat" to "پیشو",
        "dog" to "سپی",
        "horse" to "آس",
        "backpack" to "بکس",
        "umbrella" to "چترۍ",
        "bottle" to "بوتل",
        "cup" to "پیاله",
        "banana" to "کېله",
        "apple" to "مڼه",
        "chair" to "څوکۍ",
        "couch" to "سوفه",
        "bed" to "کټ",
        "dining table" to "د ډوډۍ مېز",
        "toilet" to "تشناب",
        "tv" to "تلویزیون",
        "laptop" to "لېپټاپ",
        "cell phone" to "ګرځنده ټیلیفون",
        "book" to "کتاب",
        "clock" to "ساعت"
    )

    fun localize(label: String, direction: String, language: String): String {
        val normalizedLabel = label.lowercase()

        return when (language) {
            "Urdu (Pakistan)" -> {
                val translatedLabel = urduLabels[normalizedLabel] ?: label
                val translatedDirection = when (direction) {
                    "front-left" -> "سامنے بائیں جانب"
                    "front-right" -> "سامنے دائیں جانب"
                    else -> "بالکل سامنے"
                }
                "آپ کے $translatedDirection ایک $translatedLabel موجود ہے۔"
            }

            "Pashto (Afghanistan)" -> {
                val translatedLabel = pashtoLabels[normalizedLabel] ?: label
                val translatedDirection = when (direction) {
                    "front-left" -> "چپ لوري ته"
                    "front-right" -> "ښي لوري ته"
                    else -> "مستقیم مخې ته"
                }
                "ستاسو مخې ته $translatedDirection $translatedLabel شتون لري."
            }

            else -> {
                val translatedDirection = when (direction) {
                    "front-left" -> "ahead of you, on your left"
                    "front-right" -> "ahead of you, on your right"
                    else -> "directly ahead of you"
                }
                "There is a $label $translatedDirection."
            }
        }
    }
}
