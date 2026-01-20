package com.julogic.jukavpn.utils

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.julogic.jukavpn.R
import java.util.Locale

/**
 * Comprehensive utility class for country codes, names, flags, and regional information
 * Supports 250+ countries and territories with complete ISO 3166-1 alpha-2 codes
 */
object CountryUtils {
    
    /**
     * Complete map of ISO 3166-1 alpha-2 country codes to country names
     */
    private val countryNames = mapOf(
        // A
        "A1" to "Anonymous Proxy",
        "A2" to "Satellite Provider",
        "AD" to "Andorra",
        "AE" to "United Arab Emirates",
        "AF" to "Afghanistan",
        "AG" to "Antigua and Barbuda",
        "AI" to "Anguilla",
        "AL" to "Albania",
        "AM" to "Armenia",
        "AO" to "Angola",
        "AQ" to "Antarctica",
        "AR" to "Argentina",
        "AS" to "American Samoa",
        "AT" to "Austria",
        "AU" to "Australia",
        "AW" to "Aruba",
        "AX" to "Åland Islands",
        "AZ" to "Azerbaijan",
        
        // B
        "BA" to "Bosnia and Herzegovina",
        "BB" to "Barbados",
        "BD" to "Bangladesh",
        "BE" to "Belgium",
        "BF" to "Burkina Faso",
        "BG" to "Bulgaria",
        "BH" to "Bahrain",
        "BI" to "Burundi",
        "BJ" to "Benin",
        "BL" to "Saint Barthélemy",
        "BM" to "Bermuda",
        "BN" to "Brunei",
        "BO" to "Bolivia",
        "BQ" to "Caribbean Netherlands",
        "BR" to "Brazil",
        "BS" to "Bahamas",
        "BT" to "Bhutan",
        "BV" to "Bouvet Island",
        "BW" to "Botswana",
        "BY" to "Belarus",
        "BZ" to "Belize",
        
        // C
        "CA" to "Canada",
        "CC" to "Cocos Islands",
        "CD" to "DR Congo",
        "CF" to "Central African Republic",
        "CG" to "Republic of the Congo",
        "CH" to "Switzerland",
        "CI" to "Ivory Coast",
        "CK" to "Cook Islands",
        "CL" to "Chile",
        "CM" to "Cameroon",
        "CN" to "China",
        "CO" to "Colombia",
        "CR" to "Costa Rica",
        "CU" to "Cuba",
        "CV" to "Cape Verde",
        "CW" to "Curaçao",
        "CX" to "Christmas Island",
        "CY" to "Cyprus",
        "CZ" to "Czech Republic",
        
        // D
        "DE" to "Germany",
        "DJ" to "Djibouti",
        "DK" to "Denmark",
        "DM" to "Dominica",
        "DO" to "Dominican Republic",
        "DZ" to "Algeria",
        
        // E
        "EC" to "Ecuador",
        "EE" to "Estonia",
        "EG" to "Egypt",
        "EH" to "Western Sahara",
        "ER" to "Eritrea",
        "ES" to "Spain",
        "ET" to "Ethiopia",
        "EU" to "European Union",
        
        // F
        "FI" to "Finland",
        "FJ" to "Fiji",
        "FK" to "Falkland Islands",
        "FM" to "Micronesia",
        "FO" to "Faroe Islands",
        "FR" to "France",
        
        // G
        "GA" to "Gabon",
        "GB" to "United Kingdom",
        "GD" to "Grenada",
        "GE" to "Georgia",
        "GF" to "French Guiana",
        "GG" to "Guernsey",
        "GH" to "Ghana",
        "GI" to "Gibraltar",
        "GL" to "Greenland",
        "GM" to "Gambia",
        "GN" to "Guinea",
        "GP" to "Guadeloupe",
        "GQ" to "Equatorial Guinea",
        "GR" to "Greece",
        "GS" to "South Georgia",
        "GT" to "Guatemala",
        "GU" to "Guam",
        "GW" to "Guinea-Bissau",
        "GY" to "Guyana",
        
        // H
        "HK" to "Hong Kong",
        "HM" to "Heard Island",
        "HN" to "Honduras",
        "HR" to "Croatia",
        "HT" to "Haiti",
        "HU" to "Hungary",
        
        // I
        "ID" to "Indonesia",
        "IE" to "Ireland",
        "IL" to "Israel",
        "IM" to "Isle of Man",
        "IN" to "India",
        "IO" to "British Indian Ocean Territory",
        "IQ" to "Iraq",
        "IR" to "Iran",
        "IS" to "Iceland",
        "IT" to "Italy",
        
        // J
        "JE" to "Jersey",
        "JM" to "Jamaica",
        "JO" to "Jordan",
        "JP" to "Japan",
        
        // K
        "KE" to "Kenya",
        "KG" to "Kyrgyzstan",
        "KH" to "Cambodia",
        "KI" to "Kiribati",
        "KM" to "Comoros",
        "KN" to "Saint Kitts and Nevis",
        "KP" to "North Korea",
        "KR" to "South Korea",
        "KW" to "Kuwait",
        "KY" to "Cayman Islands",
        "KZ" to "Kazakhstan",
        
        // L
        "LA" to "Laos",
        "LB" to "Lebanon",
        "LC" to "Saint Lucia",
        "LI" to "Liechtenstein",
        "LK" to "Sri Lanka",
        "LR" to "Liberia",
        "LS" to "Lesotho",
        "LT" to "Lithuania",
        "LU" to "Luxembourg",
        "LV" to "Latvia",
        "LY" to "Libya",
        
        // M
        "MA" to "Morocco",
        "MC" to "Monaco",
        "MD" to "Moldova",
        "ME" to "Montenegro",
        "MF" to "Saint Martin",
        "MG" to "Madagascar",
        "MH" to "Marshall Islands",
        "MK" to "North Macedonia",
        "ML" to "Mali",
        "MM" to "Myanmar",
        "MN" to "Mongolia",
        "MO" to "Macau",
        "MP" to "Northern Mariana Islands",
        "MQ" to "Martinique",
        "MR" to "Mauritania",
        "MS" to "Montserrat",
        "MT" to "Malta",
        "MU" to "Mauritius",
        "MV" to "Maldives",
        "MW" to "Malawi",
        "MX" to "Mexico",
        "MY" to "Malaysia",
        "MZ" to "Mozambique",
        
        // N
        "NA" to "Namibia",
        "NC" to "New Caledonia",
        "NE" to "Niger",
        "NF" to "Norfolk Island",
        "NG" to "Nigeria",
        "NI" to "Nicaragua",
        "NL" to "Netherlands",
        "NO" to "Norway",
        "NP" to "Nepal",
        "NR" to "Nauru",
        "NU" to "Niue",
        "NZ" to "New Zealand",
        
        // O
        "O1" to "Other",
        "OM" to "Oman",
        
        // P
        "PA" to "Panama",
        "PE" to "Peru",
        "PF" to "French Polynesia",
        "PG" to "Papua New Guinea",
        "PH" to "Philippines",
        "PK" to "Pakistan",
        "PL" to "Poland",
        "PM" to "Saint Pierre and Miquelon",
        "PN" to "Pitcairn Islands",
        "PR" to "Puerto Rico",
        "PS" to "Palestine",
        "PT" to "Portugal",
        "PW" to "Palau",
        "PY" to "Paraguay",
        
        // Q
        "QA" to "Qatar",
        
        // R
        "RE" to "Réunion",
        "RO" to "Romania",
        "RS" to "Serbia",
        "RU" to "Russia",
        "RW" to "Rwanda",
        
        // S
        "SA" to "Saudi Arabia",
        "SB" to "Solomon Islands",
        "SC" to "Seychelles",
        "SD" to "Sudan",
        "SE" to "Sweden",
        "SG" to "Singapore",
        "SH" to "Saint Helena",
        "SI" to "Slovenia",
        "SJ" to "Svalbard and Jan Mayen",
        "SK" to "Slovakia",
        "SL" to "Sierra Leone",
        "SM" to "San Marino",
        "SN" to "Senegal",
        "SO" to "Somalia",
        "SR" to "Suriname",
        "SS" to "South Sudan",
        "ST" to "São Tomé and Príncipe",
        "SV" to "El Salvador",
        "SX" to "Sint Maarten",
        "SY" to "Syria",
        "SZ" to "Eswatini",
        
        // T
        "TC" to "Turks and Caicos",
        "TD" to "Chad",
        "TF" to "French Southern Territories",
        "TG" to "Togo",
        "TH" to "Thailand",
        "TJ" to "Tajikistan",
        "TK" to "Tokelau",
        "TL" to "Timor-Leste",
        "TM" to "Turkmenistan",
        "TN" to "Tunisia",
        "TO" to "Tonga",
        "TR" to "Turkey",
        "TT" to "Trinidad and Tobago",
        "TV" to "Tuvalu",
        "TW" to "Taiwan",
        "TZ" to "Tanzania",
        
        // U
        "UA" to "Ukraine",
        "UG" to "Uganda",
        "UK" to "United Kingdom",
        "UM" to "U.S. Minor Outlying Islands",
        "UN" to "Unknown",
        "US" to "United States",
        "UY" to "Uruguay",
        "UZ" to "Uzbekistan",
        
        // V
        "VA" to "Vatican City",
        "VC" to "Saint Vincent and the Grenadines",
        "VE" to "Venezuela",
        "VG" to "British Virgin Islands",
        "VI" to "U.S. Virgin Islands",
        "VN" to "Vietnam",
        "VU" to "Vanuatu",
        
        // W
        "WF" to "Wallis and Futuna",
        "WS" to "Samoa",
        
        // X
        "XK" to "Kosovo",
        
        // Y
        "YE" to "Yemen",
        "YT" to "Mayotte",
        
        // Z
        "ZA" to "South Africa",
        "ZM" to "Zambia",
        "ZW" to "Zimbabwe"
    )
    
    /**
     * Map of country codes to continent/region
     */
    private val countryRegions = mapOf(
        // Europe
        "AD" to "Europe", "AL" to "Europe", "AT" to "Europe", "BA" to "Europe",
        "BE" to "Europe", "BG" to "Europe", "BY" to "Europe", "CH" to "Europe",
        "CY" to "Europe", "CZ" to "Europe", "DE" to "Europe", "DK" to "Europe",
        "EE" to "Europe", "ES" to "Europe", "FI" to "Europe", "FO" to "Europe",
        "FR" to "Europe", "GB" to "Europe", "GE" to "Europe", "GI" to "Europe",
        "GR" to "Europe", "HR" to "Europe", "HU" to "Europe", "IE" to "Europe",
        "IS" to "Europe", "IT" to "Europe", "LI" to "Europe", "LT" to "Europe",
        "LU" to "Europe", "LV" to "Europe", "MC" to "Europe", "MD" to "Europe",
        "ME" to "Europe", "MK" to "Europe", "MT" to "Europe", "NL" to "Europe",
        "NO" to "Europe", "PL" to "Europe", "PT" to "Europe", "RO" to "Europe",
        "RS" to "Europe", "RU" to "Europe", "SE" to "Europe", "SI" to "Europe",
        "SK" to "Europe", "SM" to "Europe", "UA" to "Europe", "UK" to "Europe",
        "VA" to "Europe", "XK" to "Europe",
        
        // Asia
        "AE" to "Asia", "AF" to "Asia", "AM" to "Asia", "AZ" to "Asia",
        "BD" to "Asia", "BH" to "Asia", "BN" to "Asia", "BT" to "Asia",
        "CN" to "Asia", "HK" to "Asia", "ID" to "Asia", "IL" to "Asia",
        "IN" to "Asia", "IQ" to "Asia", "IR" to "Asia", "JO" to "Asia",
        "JP" to "Asia", "KG" to "Asia", "KH" to "Asia", "KP" to "Asia",
        "KR" to "Asia", "KW" to "Asia", "KZ" to "Asia", "LA" to "Asia",
        "LB" to "Asia", "LK" to "Asia", "MM" to "Asia", "MN" to "Asia",
        "MO" to "Asia", "MV" to "Asia", "MY" to "Asia", "NP" to "Asia",
        "OM" to "Asia", "PH" to "Asia", "PK" to "Asia", "PS" to "Asia",
        "QA" to "Asia", "SA" to "Asia", "SG" to "Asia", "SY" to "Asia",
        "TH" to "Asia", "TJ" to "Asia", "TL" to "Asia", "TM" to "Asia",
        "TR" to "Asia", "TW" to "Asia", "UZ" to "Asia", "VN" to "Asia",
        "YE" to "Asia",
        
        // Africa
        "AO" to "Africa", "BF" to "Africa", "BI" to "Africa", "BJ" to "Africa",
        "BW" to "Africa", "CD" to "Africa", "CF" to "Africa", "CG" to "Africa",
        "CI" to "Africa", "CM" to "Africa", "CV" to "Africa", "DJ" to "Africa",
        "DZ" to "Africa", "EG" to "Africa", "EH" to "Africa", "ER" to "Africa",
        "ET" to "Africa", "GA" to "Africa", "GH" to "Africa", "GM" to "Africa",
        "GN" to "Africa", "GQ" to "Africa", "GW" to "Africa", "KE" to "Africa",
        "KM" to "Africa", "LR" to "Africa", "LS" to "Africa", "LY" to "Africa",
        "MA" to "Africa", "MG" to "Africa", "ML" to "Africa", "MR" to "Africa",
        "MU" to "Africa", "MW" to "Africa", "MZ" to "Africa", "NA" to "Africa",
        "NE" to "Africa", "NG" to "Africa", "RE" to "Africa", "RW" to "Africa",
        "SC" to "Africa", "SD" to "Africa", "SL" to "Africa", "SN" to "Africa",
        "SO" to "Africa", "SS" to "Africa", "ST" to "Africa", "SZ" to "Africa",
        "TD" to "Africa", "TG" to "Africa", "TN" to "Africa", "TZ" to "Africa",
        "UG" to "Africa", "YT" to "Africa", "ZA" to "Africa", "ZM" to "Africa",
        "ZW" to "Africa",
        
        // North America
        "AG" to "North America", "AI" to "North America", "AW" to "North America",
        "BB" to "North America", "BL" to "North America", "BM" to "North America",
        "BQ" to "North America", "BS" to "North America", "BZ" to "North America",
        "CA" to "North America", "CR" to "North America", "CU" to "North America",
        "CW" to "North America", "DM" to "North America", "DO" to "North America",
        "GD" to "North America", "GL" to "North America", "GP" to "North America",
        "GT" to "North America", "HN" to "North America", "HT" to "North America",
        "JM" to "North America", "KN" to "North America", "KY" to "North America",
        "LC" to "North America", "MF" to "North America", "MQ" to "North America",
        "MS" to "North America", "MX" to "North America", "NI" to "North America",
        "PA" to "North America", "PM" to "North America", "PR" to "North America",
        "SV" to "North America", "SX" to "North America", "TC" to "North America",
        "TT" to "North America", "US" to "North America", "VC" to "North America",
        "VG" to "North America", "VI" to "North America",
        
        // South America
        "AR" to "South America", "BO" to "South America", "BR" to "South America",
        "CL" to "South America", "CO" to "South America", "EC" to "South America",
        "FK" to "South America", "GF" to "South America", "GY" to "South America",
        "PE" to "South America", "PY" to "South America", "SR" to "South America",
        "UY" to "South America", "VE" to "South America",
        
        // Oceania
        "AS" to "Oceania", "AU" to "Oceania", "CC" to "Oceania", "CK" to "Oceania",
        "CX" to "Oceania", "FJ" to "Oceania", "FM" to "Oceania", "GU" to "Oceania",
        "HM" to "Oceania", "KI" to "Oceania", "MH" to "Oceania", "MP" to "Oceania",
        "NC" to "Oceania", "NF" to "Oceania", "NR" to "Oceania", "NU" to "Oceania",
        "NZ" to "Oceania", "PF" to "Oceania", "PG" to "Oceania", "PN" to "Oceania",
        "PW" to "Oceania", "SB" to "Oceania", "TK" to "Oceania", "TO" to "Oceania",
        "TV" to "Oceania", "VU" to "Oceania", "WF" to "Oceania", "WS" to "Oceania"
    )
    
    /**
     * Map of common country name variations to standard codes
     */
    private val countryAliases = mapOf(
        // English variations
        "united states" to "US",
        "usa" to "US",
        "u.s.a." to "US",
        "america" to "US",
        "united kingdom" to "GB",
        "uk" to "GB",
        "england" to "GB",
        "britain" to "GB",
        "great britain" to "GB",
        "deutschland" to "DE",
        "germany" to "DE",
        "france" to "FR",
        "japan" to "JP",
        "nippon" to "JP",
        "china" to "CN",
        "zhongguo" to "CN",
        "korea" to "KR",
        "south korea" to "KR",
        "north korea" to "KP",
        "russia" to "RU",
        "russian federation" to "RU",
        "netherlands" to "NL",
        "holland" to "NL",
        "hong kong" to "HK",
        "hongkong" to "HK",
        "taiwan" to "TW",
        "singapore" to "SG",
        "india" to "IN",
        "australia" to "AU",
        "canada" to "CA",
        "brazil" to "BR",
        "brasil" to "BR",
        "mexico" to "MX",
        "spain" to "ES",
        "españa" to "ES",
        "italy" to "IT",
        "italia" to "IT",
        "switzerland" to "CH",
        "schweiz" to "CH",
        "suisse" to "CH",
        "sweden" to "SE",
        "norge" to "NO",
        "norway" to "NO",
        "finland" to "FI",
        "suomi" to "FI",
        "denmark" to "DK",
        "danmark" to "DK",
        "poland" to "PL",
        "polska" to "PL",
        "turkey" to "TR",
        "türkiye" to "TR",
        "ukraine" to "UA",
        "ucrania" to "UA",
        "israel" to "IL",
        "south africa" to "ZA",
        "argentina" to "AR",
        "chile" to "CL",
        "colombia" to "CO",
        "peru" to "PE",
        "venezuela" to "VE",
        "egypt" to "EG",
        "thailand" to "TH",
        "vietnam" to "VN",
        "philippines" to "PH",
        "indonesia" to "ID",
        "malaysia" to "MY",
        "pakistan" to "PK",
        "iran" to "IR",
        "iraq" to "IQ",
        "saudi arabia" to "SA",
        "united arab emirates" to "AE",
        "uae" to "AE",
        "dubai" to "AE",
        "new zealand" to "NZ",
        "ireland" to "IE",
        "scotland" to "GB",
        "wales" to "GB",
        "austria" to "AT",
        "österreich" to "AT",
        "belgium" to "BE",
        "belgique" to "BE",
        "czech republic" to "CZ",
        "czechia" to "CZ",
        "greece" to "GR",
        "portugal" to "PT",
        "romania" to "RO",
        "hungary" to "HU",
        "bulgaria" to "BG",
        "croatia" to "HR",
        "serbia" to "RS",
        "slovenia" to "SI",
        "slovakia" to "SK",
        "lithuania" to "LT",
        "latvia" to "LV",
        "estonia" to "EE",
        "iceland" to "IS",
        "luxembourg" to "LU",
        "monaco" to "MC",
        "cyprus" to "CY",
        "malta" to "MT",
        "macau" to "MO",
        "macao" to "MO"
    )
    
    /**
     * Map of flag emojis to country codes (for parsing)
     */
    private val emojiToCode = mapOf(
        "🇦🇩" to "AD", "🇦🇪" to "AE", "🇦🇫" to "AF", "🇦🇬" to "AG", "🇦🇮" to "AI",
        "🇦🇱" to "AL", "🇦🇲" to "AM", "🇦🇴" to "AO", "🇦🇶" to "AQ", "🇦🇷" to "AR",
        "🇦🇸" to "AS", "🇦🇹" to "AT", "🇦🇺" to "AU", "🇦🇼" to "AW", "🇦🇽" to "AX",
        "🇦🇿" to "AZ", "🇧🇦" to "BA", "🇧🇧" to "BB", "🇧🇩" to "BD", "🇧🇪" to "BE",
        "🇧🇫" to "BF", "🇧🇬" to "BG", "🇧🇭" to "BH", "🇧🇮" to "BI", "🇧🇯" to "BJ",
        "🇧🇱" to "BL", "🇧🇲" to "BM", "🇧🇳" to "BN", "🇧🇴" to "BO", "🇧🇶" to "BQ",
        "🇧🇷" to "BR", "🇧🇸" to "BS", "🇧🇹" to "BT", "🇧🇻" to "BV", "🇧🇼" to "BW",
        "🇧🇾" to "BY", "🇧🇿" to "BZ", "🇨🇦" to "CA", "🇨🇨" to "CC", "🇨🇩" to "CD",
        "🇨🇫" to "CF", "🇨🇬" to "CG", "🇨🇭" to "CH", "🇨🇮" to "CI", "🇨🇰" to "CK",
        "🇨🇱" to "CL", "🇨🇲" to "CM", "🇨🇳" to "CN", "🇨🇴" to "CO", "🇨🇷" to "CR",
        "🇨🇺" to "CU", "🇨🇻" to "CV", "🇨🇼" to "CW", "🇨🇽" to "CX", "🇨🇾" to "CY",
        "🇨🇿" to "CZ", "🇩🇪" to "DE", "🇩🇯" to "DJ", "🇩🇰" to "DK", "🇩🇲" to "DM",
        "🇩🇴" to "DO", "🇩🇿" to "DZ", "🇪🇨" to "EC", "🇪🇪" to "EE", "🇪🇬" to "EG",
        "🇪🇭" to "EH", "🇪🇷" to "ER", "🇪🇸" to "ES", "🇪🇹" to "ET", "🇪🇺" to "EU",
        "🇫🇮" to "FI", "🇫🇯" to "FJ", "🇫🇰" to "FK", "🇫🇲" to "FM", "🇫🇴" to "FO",
        "🇫🇷" to "FR", "🇬🇦" to "GA", "🇬🇧" to "GB", "🇬🇩" to "GD", "🇬🇪" to "GE",
        "🇬🇫" to "GF", "🇬🇬" to "GG", "🇬🇭" to "GH", "🇬🇮" to "GI", "🇬🇱" to "GL",
        "🇬🇲" to "GM", "🇬🇳" to "GN", "🇬🇵" to "GP", "🇬🇶" to "GQ", "🇬🇷" to "GR",
        "🇬🇸" to "GS", "🇬🇹" to "GT", "🇬🇺" to "GU", "🇬🇼" to "GW", "🇬🇾" to "GY",
        "🇭🇰" to "HK", "🇭🇲" to "HM", "🇭🇳" to "HN", "🇭🇷" to "HR", "🇭🇹" to "HT",
        "🇭🇺" to "HU", "🇮🇩" to "ID", "🇮🇪" to "IE", "🇮🇱" to "IL", "🇮🇲" to "IM",
        "🇮🇳" to "IN", "🇮🇴" to "IO", "🇮🇶" to "IQ", "🇮🇷" to "IR", "🇮🇸" to "IS",
        "🇮🇹" to "IT", "🇯🇪" to "JE", "🇯🇲" to "JM", "🇯🇴" to "JO", "🇯🇵" to "JP",
        "🇰🇪" to "KE", "🇰🇬" to "KG", "🇰🇭" to "KH", "🇰🇮" to "KI", "🇰🇲" to "KM",
        "🇰🇳" to "KN", "🇰🇵" to "KP", "🇰🇷" to "KR", "🇰🇼" to "KW", "🇰🇾" to "KY",
        "🇰🇿" to "KZ", "🇱🇦" to "LA", "🇱🇧" to "LB", "🇱🇨" to "LC", "🇱🇮" to "LI",
        "🇱🇰" to "LK", "🇱🇷" to "LR", "🇱🇸" to "LS", "🇱🇹" to "LT", "🇱🇺" to "LU",
        "🇱🇻" to "LV", "🇱🇾" to "LY", "🇲🇦" to "MA", "🇲🇨" to "MC", "🇲🇩" to "MD",
        "🇲🇪" to "ME", "🇲🇫" to "MF", "🇲🇬" to "MG", "🇲🇭" to "MH", "🇲🇰" to "MK",
        "🇲🇱" to "ML", "🇲🇲" to "MM", "🇲🇳" to "MN", "🇲🇴" to "MO", "🇲🇵" to "MP",
        "🇲🇶" to "MQ", "🇲🇷" to "MR", "🇲🇸" to "MS", "🇲🇹" to "MT", "🇲🇺" to "MU",
        "🇲🇻" to "MV", "🇲🇼" to "MW", "🇲🇽" to "MX", "🇲🇾" to "MY", "🇲🇿" to "MZ",
        "🇳🇦" to "NA", "🇳🇨" to "NC", "🇳🇪" to "NE", "🇳🇫" to "NF", "🇳🇬" to "NG",
        "🇳🇮" to "NI", "🇳🇱" to "NL", "🇳🇴" to "NO", "🇳🇵" to "NP", "🇳🇷" to "NR",
        "🇳🇺" to "NU", "🇳🇿" to "NZ", "🇴🇲" to "OM", "🇵🇦" to "PA", "🇵🇪" to "PE",
        "🇵🇫" to "PF", "🇵🇬" to "PG", "🇵🇭" to "PH", "🇵🇰" to "PK", "🇵🇱" to "PL",
        "🇵🇲" to "PM", "🇵🇳" to "PN", "🇵🇷" to "PR", "🇵🇸" to "PS", "🇵🇹" to "PT",
        "🇵🇼" to "PW", "🇵🇾" to "PY", "🇶🇦" to "QA", "🇷🇪" to "RE", "🇷🇴" to "RO",
        "🇷🇸" to "RS", "🇷🇺" to "RU", "🇷🇼" to "RW", "🇸🇦" to "SA", "🇸🇧" to "SB",
        "🇸🇨" to "SC", "🇸🇩" to "SD", "🇸🇪" to "SE", "🇸🇬" to "SG", "🇸🇭" to "SH",
        "🇸🇮" to "SI", "🇸🇯" to "SJ", "🇸🇰" to "SK", "🇸🇱" to "SL", "🇸🇲" to "SM",
        "🇸🇳" to "SN", "🇸🇴" to "SO", "🇸🇷" to "SR", "🇸🇸" to "SS", "🇸🇹" to "ST",
        "🇸🇻" to "SV", "🇸🇽" to "SX", "🇸🇾" to "SY", "🇸🇿" to "SZ", "🇹🇨" to "TC",
        "🇹🇩" to "TD", "🇹🇫" to "TF", "🇹🇬" to "TG", "🇹🇭" to "TH", "🇹🇯" to "TJ",
        "🇹🇰" to "TK", "🇹🇱" to "TL", "🇹🇲" to "TM", "🇹🇳" to "TN", "🇹🇴" to "TO",
        "🇹🇷" to "TR", "🇹🇹" to "TT", "🇹🇻" to "TV", "🇹🇼" to "TW", "🇹🇿" to "TZ",
        "🇺🇦" to "UA", "🇺🇬" to "UG", "🇺🇲" to "UM", "🇺🇳" to "UN", "🇺🇸" to "US",
        "🇺🇾" to "UY", "🇺🇿" to "UZ", "🇻🇦" to "VA", "🇻🇨" to "VC", "🇻🇪" to "VE",
        "🇻🇬" to "VG", "🇻🇮" to "VI", "🇻🇳" to "VN", "🇻🇺" to "VU", "🇼🇫" to "WF",
        "🇼🇸" to "WS", "🇽🇰" to "XK", "🇾🇪" to "YE", "🇾🇹" to "YT", "🇿🇦" to "ZA",
        "🇿🇲" to "ZM", "🇿🇼" to "ZW"
    )
    
    /**
     * Get country name from code
     */
    fun getCountryName(code: String): String {
        return countryNames[code.uppercase()] ?: "Unknown"
    }
    
    /**
     * Get localized country name using device locale
     */
    fun getLocalizedCountryName(code: String): String {
        return try {
            val locale = Locale("", code.uppercase())
            val displayName = locale.displayCountry
            if (displayName.isNotEmpty() && displayName != code) {
                displayName
            } else {
                getCountryName(code)
            }
        } catch (e: Exception) {
            getCountryName(code)
        }
    }
    
    /**
     * Get flag emoji from country code
     */
    fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "🏳️"
        
        val code = countryCode.uppercase()
        if (code == "UN" || code == "A1" || code == "A2" || code == "O1") return "🏳️"
        
        return try {
            val firstLetter = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
            val secondLetter = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
        } catch (e: Exception) {
            "🏳️"
        }
    }
    
    /**
     * Get country code from flag emoji
     */
    fun getCountryCodeFromEmoji(emoji: String): String? {
        return emojiToCode[emoji]
    }
    
    /**
     * Get flag drawable resource ID
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
     * Get flag drawable
     */
    fun getFlagDrawable(context: Context, countryCode: String): Drawable? {
        val resId = getFlagDrawableId(context, countryCode)
        return try {
            ContextCompat.getDrawable(context, resId)
        } catch (e: Exception) {
            ContextCompat.getDrawable(context, R.drawable.flag_unknown)
        }
    }
    
    /**
     * Check if flag drawable exists for country
     */
    fun hasFlagDrawable(context: Context, countryCode: String): Boolean {
        val code = countryCode.lowercase()
        val resourceName = "flag_$code"
        val resId = context.resources.getIdentifier(
            resourceName,
            "drawable",
            context.packageName
        )
        return resId != 0
    }
    
    /**
     * Get region/continent for country code
     */
    fun getRegion(countryCode: String): String {
        return countryRegions[countryCode.uppercase()] ?: "Unknown"
    }
    
    /**
     * Get all country codes
     */
    fun getAllCountryCodes(): List<String> {
        return countryNames.keys.toList().sorted()
    }
    
    /**
     * Get all countries as pairs of (code, name)
     */
    fun getAllCountries(): List<Pair<String, String>> {
        return countryNames.entries
            .map { it.key to it.value }
            .sortedBy { it.second }
    }
    
    /**
     * Get countries by region
     */
    fun getCountriesByRegion(region: String): List<Pair<String, String>> {
        return countryRegions.entries
            .filter { it.value.equals(region, ignoreCase = true) }
            .map { it.key to getCountryName(it.key) }
            .sortedBy { it.second }
    }
    
    /**
     * Get all available regions
     */
    fun getAllRegions(): List<String> {
        return countryRegions.values.distinct().sorted()
    }
    
    /**
     * Search countries by name or code
     */
    fun searchCountries(query: String): List<Pair<String, String>> {
        val lowercaseQuery = query.lowercase().trim()
        if (lowercaseQuery.isEmpty()) return getAllCountries()
        
        // Check aliases first
        val aliasMatch = countryAliases[lowercaseQuery]
        if (aliasMatch != null) {
            return listOf(aliasMatch to getCountryName(aliasMatch))
        }
        
        return countryNames.entries
            .filter { (code, name) ->
                code.lowercase().contains(lowercaseQuery) ||
                name.lowercase().contains(lowercaseQuery)
            }
            .map { (code, name) -> code to name }
            .sortedBy { it.second }
    }
    
    /**
     * Resolve country code from various inputs (code, name, alias, emoji)
     */
    fun resolveCountryCode(input: String): String {
        val trimmed = input.trim()
        
        // Check if it's already a valid 2-letter code
        if (trimmed.length == 2 && countryNames.containsKey(trimmed.uppercase())) {
            return trimmed.uppercase()
        }
        
        // Check if it's a flag emoji
        val emojiCode = getCountryCodeFromEmoji(trimmed)
        if (emojiCode != null) {
            return emojiCode
        }
        
        // Check aliases
        val aliasMatch = countryAliases[trimmed.lowercase()]
        if (aliasMatch != null) {
            return aliasMatch
        }
        
        // Search by name
        val nameMatch = countryNames.entries.find { (_, name) ->
            name.equals(trimmed, ignoreCase = true)
        }
        if (nameMatch != null) {
            return nameMatch.key
        }
        
        // Partial match
        val partialMatch = countryNames.entries.find { (_, name) ->
            name.lowercase().contains(trimmed.lowercase())
        }
        if (partialMatch != null) {
            return partialMatch.key
        }
        
        return "UN"
    }
    
    /**
     * Extract country code from server name or hostname
     */
    fun extractCountryCode(text: String): String {
        val upperText = text.uppercase()
        
        // Try to find flag emoji first
        for ((emoji, code) in emojiToCode) {
            if (text.contains(emoji)) {
                return code
            }
        }
        
        // Try to find 2-letter code in brackets or after common patterns
        val patterns = listOf(
            Regex("\\[([A-Z]{2})]"),                    // [US]
            Regex("\\(([A-Z]{2})\\)"),                   // (US)
            Regex("^([A-Z]{2})[\\s_-]"),                 // US-Server
            Regex("[\\s_-]([A-Z]{2})$"),                 // Server-US
            Regex("[\\s_-]([A-Z]{2})[\\s_-]"),           // Server-US-01
            Regex("(?:^|[\\s_-])([A-Z]{2})(?:\\d+|$)")   // US01 or US
        )
        
        for (pattern in patterns) {
            val match = pattern.find(upperText)
            if (match != null) {
                val code = match.groupValues[1]
                if (countryNames.containsKey(code)) {
                    return code
                }
            }
        }
        
        // Try to find country name
        for ((alias, code) in countryAliases) {
            if (text.lowercase().contains(alias)) {
                return code
            }
        }
        
        // Try standard country names
        for ((code, name) in countryNames) {
            if (text.lowercase().contains(name.lowercase())) {
                return code
            }
        }
        
        // Try to extract from hostname patterns like us.example.com
        val hostnamePattern = Regex("^([a-z]{2})\\d*\\.")
        val hostnameMatch = hostnamePattern.find(text.lowercase())
        if (hostnameMatch != null) {
            val code = hostnameMatch.groupValues[1].uppercase()
            if (countryNames.containsKey(code)) {
                return code
            }
        }
        
        return "UN"
    }
    
    /**
     * Group items by country
     */
    fun <T> groupByCountry(
        items: List<T>,
        getCountryCode: (T) -> String
    ): Map<String, List<T>> {
        return items.groupBy { getCountryCode(it).uppercase() }
            .toSortedMap(compareBy { getCountryName(it) })
    }
    
    /**
     * Group items by region
     */
    fun <T> groupByRegion(
        items: List<T>,
        getCountryCode: (T) -> String
    ): Map<String, List<T>> {
        return items.groupBy { getRegion(getCountryCode(it)) }
            .toSortedMap()
    }
    
    /**
     * Sort countries by distance from reference country (basic implementation)
     * Uses region proximity as a simple heuristic
     */
    fun sortByProximity(
        countryCodes: List<String>,
        referenceCode: String
    ): List<String> {
        val refRegion = getRegion(referenceCode)
        
        return countryCodes.sortedWith(compareBy(
            { if (it == referenceCode) 0 else 1 },     // Same country first
            { if (getRegion(it) == refRegion) 0 else 1 }, // Same region second
            { getCountryName(it) }                        // Alphabetical
        ))
    }
    
    /**
     * Get country statistics
     */
    data class CountryStats(
        val code: String,
        val name: String,
        val region: String,
        val flagEmoji: String
    )
    
    fun getCountryStats(code: String): CountryStats {
        val upperCode = code.uppercase()
        return CountryStats(
            code = upperCode,
            name = getCountryName(upperCode),
            region = getRegion(upperCode),
            flagEmoji = getFlagEmoji(upperCode)
        )
    }
    
    /**
     * Validate country code
     */
    fun isValidCountryCode(code: String): Boolean {
        return code.length == 2 && countryNames.containsKey(code.uppercase())
    }
    
    /**
     * Get popular VPN countries (commonly used server locations)
     */
    fun getPopularVpnCountries(): List<String> {
        return listOf(
            "US", "GB", "DE", "NL", "FR", "CH", "SE", "NO", "DK", "FI",
            "CA", "AU", "JP", "SG", "HK", "KR", "TW", "IN", "BR", "MX",
            "IT", "ES", "PL", "CZ", "AT", "BE", "IE", "PT", "RO", "BG",
            "RU", "UA", "TR", "IL", "AE", "ZA", "NZ", "AR", "CL", "CO"
        )
    }
    
    /**
     * Format display string with flag and name
     */
    fun formatCountryDisplay(code: String, includeFlagEmoji: Boolean = true): String {
        val name = getCountryName(code)
        return if (includeFlagEmoji) {
            "${getFlagEmoji(code)} $name"
        } else {
            name
        }
    }
}
