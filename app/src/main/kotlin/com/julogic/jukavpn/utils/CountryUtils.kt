package com.julogic.jukavpn.utils

import android.content.Context
import com.julogic.jukavpn.R

/**
 * Utility class for country codes, names, and flag resources
 */
object CountryUtils {
    
    /**
     * Map of country codes to country names
     */
    private val countryNames = mapOf(
        "AD" to "Andorra",
        "AE" to "United Arab Emirates",
        "AF" to "Afghanistan",
        "AG" to "Antigua and Barbuda",
        "AL" to "Albania",
        "AM" to "Armenia",
        "AO" to "Angola",
        "AR" to "Argentina",
        "AT" to "Austria",
        "AU" to "Australia",
        "AZ" to "Azerbaijan",
        "BA" to "Bosnia and Herzegovina",
        "BD" to "Bangladesh",
        "BE" to "Belgium",
        "BG" to "Bulgaria",
        "BH" to "Bahrain",
        "BI" to "Burundi",
        "BN" to "Brunei",
        "BO" to "Bolivia",
        "BR" to "Brazil",
        "BS" to "Bahamas",
        "BW" to "Botswana",
        "BY" to "Belarus",
        "CA" to "Canada",
        "CD" to "DR Congo",
        "CF" to "Central African Republic",
        "CH" to "Switzerland",
        "CI" to "Ivory Coast",
        "CL" to "Chile",
        "CM" to "Cameroon",
        "CN" to "China",
        "CO" to "Colombia",
        "CR" to "Costa Rica",
        "CU" to "Cuba",
        "CY" to "Cyprus",
        "CZ" to "Czech Republic",
        "DE" to "Germany",
        "DJ" to "Djibouti",
        "DK" to "Denmark",
        "DO" to "Dominican Republic",
        "DZ" to "Algeria",
        "EC" to "Ecuador",
        "EE" to "Estonia",
        "EG" to "Egypt",
        "ES" to "Spain",
        "ET" to "Ethiopia",
        "FI" to "Finland",
        "FR" to "France",
        "GA" to "Gabon",
        "GB" to "United Kingdom",
        "GE" to "Georgia",
        "GH" to "Ghana",
        "GR" to "Greece",
        "GT" to "Guatemala",
        "HK" to "Hong Kong",
        "HN" to "Honduras",
        "HR" to "Croatia",
        "HU" to "Hungary",
        "ID" to "Indonesia",
        "IE" to "Ireland",
        "IL" to "Israel",
        "IN" to "India",
        "IQ" to "Iraq",
        "IR" to "Iran",
        "IS" to "Iceland",
        "IT" to "Italy",
        "JM" to "Jamaica",
        "JO" to "Jordan",
        "JP" to "Japan",
        "KE" to "Kenya",
        "KG" to "Kyrgyzstan",
        "KH" to "Cambodia",
        "KR" to "South Korea",
        "KW" to "Kuwait",
        "KZ" to "Kazakhstan",
        "LA" to "Laos",
        "LB" to "Lebanon",
        "LI" to "Liechtenstein",
        "LK" to "Sri Lanka",
        "LT" to "Lithuania",
        "LU" to "Luxembourg",
        "LV" to "Latvia",
        "LY" to "Libya",
        "MA" to "Morocco",
        "MC" to "Monaco",
        "MD" to "Moldova",
        "ME" to "Montenegro",
        "MG" to "Madagascar",
        "MK" to "North Macedonia",
        "ML" to "Mali",
        "MM" to "Myanmar",
        "MN" to "Mongolia",
        "MO" to "Macau",
        "MT" to "Malta",
        "MU" to "Mauritius",
        "MV" to "Maldives",
        "MX" to "Mexico",
        "MY" to "Malaysia",
        "MZ" to "Mozambique",
        "NA" to "Namibia",
        "NG" to "Nigeria",
        "NI" to "Nicaragua",
        "NL" to "Netherlands",
        "NO" to "Norway",
        "NP" to "Nepal",
        "NZ" to "New Zealand",
        "OM" to "Oman",
        "PA" to "Panama",
        "PE" to "Peru",
        "PH" to "Philippines",
        "PK" to "Pakistan",
        "PL" to "Poland",
        "PR" to "Puerto Rico",
        "PT" to "Portugal",
        "PY" to "Paraguay",
        "QA" to "Qatar",
        "RO" to "Romania",
        "RS" to "Serbia",
        "RU" to "Russia",
        "RW" to "Rwanda",
        "SA" to "Saudi Arabia",
        "SD" to "Sudan",
        "SE" to "Sweden",
        "SG" to "Singapore",
        "SI" to "Slovenia",
        "SK" to "Slovakia",
        "SN" to "Senegal",
        "SO" to "Somalia",
        "SY" to "Syria",
        "TH" to "Thailand",
        "TJ" to "Tajikistan",
        "TM" to "Turkmenistan",
        "TN" to "Tunisia",
        "TR" to "Turkey",
        "TW" to "Taiwan",
        "TZ" to "Tanzania",
        "UA" to "Ukraine",
        "UG" to "Uganda",
        "UN" to "Unknown",
        "US" to "United States",
        "UY" to "Uruguay",
        "UZ" to "Uzbekistan",
        "VE" to "Venezuela",
        "VN" to "Vietnam",
        "YE" to "Yemen",
        "ZA" to "South Africa",
        "ZM" to "Zambia",
        "ZW" to "Zimbabwe"
    )
    
    /**
     * Get country name from code
     */
    fun getCountryName(code: String): String {
        return countryNames[code.uppercase()] ?: "Unknown"
    }
    
    /**
     * Get flag emoji from country code
     */
    fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "🏳️"
        
        val code = countryCode.uppercase()
        if (code == "UN") return "🏳️"
        
        return try {
            val firstLetter = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
            val secondLetter = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
        } catch (e: Exception) {
            "🏳️"
        }
    }
    
    /**
     * Get flag drawable resource ID
     * Note: You need to add flag drawables to res/drawable
     * Named as: flag_xx.png where xx is lowercase country code
     */
    fun getFlagDrawableId(context: Context, countryCode: String): Int {
        val code = countryCode.lowercase()
        val resourceName = "flag_$code"
        
        val resId = context.resources.getIdentifier(
            resourceName,
            "drawable",
            context.packageName
        )
        
        return if (resId != 0) resId else R.drawable.flag_unknown
    }
    
    /**
     * Get all country codes
     */
    fun getAllCountryCodes(): List<String> {
        return countryNames.keys.toList().sorted()
    }
    
    /**
     * Search countries by name or code
     */
    fun searchCountries(query: String): List<Pair<String, String>> {
        val lowercaseQuery = query.lowercase()
        return countryNames.entries
            .filter { (code, name) ->
                code.lowercase().contains(lowercaseQuery) ||
                name.lowercase().contains(lowercaseQuery)
            }
            .map { (code, name) -> code to name }
            .sortedBy { it.second }
    }
    
    /**
     * Group servers by country
     */
    fun <T> groupByCountry(
        items: List<T>,
        getCountryCode: (T) -> String
    ): Map<String, List<T>> {
        return items.groupBy { getCountryCode(it).uppercase() }
            .toSortedMap(compareBy { getCountryName(it) })
    }
}
