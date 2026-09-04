package com.example.model

enum class DeviceLayoutMode(val displayName: String, val description: String) {
    AUTO("Auto (Default)", "Automatically switches between Mobile and Tablet based on screen size"),
    MOBILE("Mobile (Phone)", "Standard single-column layout with bottom navigation bar"),
    TABLET("Tablet (Expanded)", "Optimized multi-column layout with side navigation rail")
}
