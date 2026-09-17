package com.flickbeam.shared

/** One entry in the "More by manuo9" screen, shown the same way on both apps. */
data class ShowcaseApp(
    val name: String,
    val description: String,
    val link: String,
)

/** Apps shown on the "More by manuo9" screen. Add new entries here as they ship. */
val ShowcaseApps: List<ShowcaseApp> = listOf(
    ShowcaseApp(
        name = "DesiPassGen",
        description = "Generate memorable Hinglish passphrases",
        link = "https://desipassgen.com",
    ),
)
