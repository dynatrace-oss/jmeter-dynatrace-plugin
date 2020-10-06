package com.dynatrace.mint;

import static java.util.Objects.requireNonNull;

import java.util.regex.Pattern;

/**
 * Used to sanitize different user controlled strings
 * of the schemaless metric used in MINT
 */

public class SchemalessMetricSanitizer {
	private static final Pattern PATTERN_INVALID_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]");
	// next two patterns could be turned into one but is not as fast (probably because capturing is involved)
	private static final Pattern PATTERN_FIRST_SECTION_INVALID_BEGINNING = Pattern.compile("^[0-9_-]+");
	private static final Pattern PATTERN_NEXT_SECTIONS_INVALID_BEGINNING = Pattern.compile("\\.[0-9_-]+");

	/**
	 * Removes all special characters from the value string (except dots, as they are used to split sections)
	 * and makes sure every section starts with a letter to satisfy the MINT specifications
	 * for a metric identifier.
	 * For use as a metric dimension identifier, just take the lowercase result.
	 *
	 * @param value the input identifier
	 * @return the transformed identifier string
	 * @see <a href="https://dev-wiki.dynatrace.org/pages/viewpage.action?spaceKey=MET&title=MINT+Specification">MINT Specification</a>
	 */
	private static String sanitizeIdentifierForMint(String value) {
		String sanitizedIdentifier = PATTERN_INVALID_CHARACTERS.matcher(value).replaceAll("_");
		// remove all numbers, dashes and underlines from start of the string
		sanitizedIdentifier = PATTERN_FIRST_SECTION_INVALID_BEGINNING.matcher(sanitizedIdentifier).replaceAll("");
		// ... and do the same after start of each subsequent section (i.e. after dot)
		sanitizedIdentifier = PATTERN_NEXT_SECTIONS_INVALID_BEGINNING.matcher(sanitizedIdentifier).replaceAll(".");

		return sanitizedIdentifier;
	}

	public static String sanitizeMetricIdentifier(final String identifier) {
		requireNonNull(identifier);

		return sanitizeIdentifierForMint(identifier);
	}

	public static String sanitizeDimensionIdentifier(final String identifier) {
		requireNonNull(identifier);

		return sanitizeIdentifierForMint(identifier).toLowerCase();
	}

	/**
	 * Wraps a String into double quotation marks, if this is required.
	 */
	public static String sanitizeDimensionValue(final String value) {
		requireNonNull(value);

		if (value.startsWith("\"") && value.endsWith("\"")) {
			return value;
		}
		if (value.indexOf(' ') > -1 || value.indexOf(',') > -1 || value.indexOf('=') > -1) {
			return "\"" + value + "\"";
		}

		return value;
	}
}
