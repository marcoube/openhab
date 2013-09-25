package org.openhab.io.rest.internal.resources;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LabelSplitHelper {
	private String label = null;
	private String format = null;
	private String map = null;
	private String unit = null;

	private static final Pattern LABEL_PATTERN = Pattern
			.compile("(.*?)\\[(.*)\\]");
	private static final Pattern MAP_PATTERN = Pattern
			.compile("(?i)MAP\\((.*?)\\):(.*)");

	final static String conversions = "bBhHsScCdoxXeEfgGaAtT%n";

	LabelSplitHelper(String itemLabel) {
		Matcher labelMatcher = LABEL_PATTERN.matcher(itemLabel);

		if (labelMatcher.matches()) {
			label = labelMatcher.group(1).trim();
			format = labelMatcher.group(2).trim();
		} else
			label = itemLabel;

		if (format != null) {
			Matcher mapMatcher = MAP_PATTERN.matcher(format);
			if (mapMatcher.matches()) {
				map = mapMatcher.group(1).trim();
				format = mapMatcher.group(2).trim();
			}
		}

		if (format != null && format.length() > 0) {
			format = format.trim();

			// Split the string according to the "Formatter" format
			// specification
			// Everything up to the last format string goes in the
			// format
			// Everything after goes in the units
			int state = 0;
			String s1 = "";
			String s2 = "";
			for (char ch : format.toCharArray()) {
				switch (state) {
				case 0:
					// Looking for start
					s2 += ch;
					if (ch == '%') {
						state = 1;
					}
					break;
				case 1:
					// Looking for end (conversion id)
					s2 += ch;
					if (ch == '%') {
						// %% is not considered part of the format -
						// it's the "unit"
						state = 0;
					} else if (conversions.indexOf(ch) != -1) {
						// This is a valid conversion ID
						s1 += s2;
						s2 = "";
						state = 0;

						// Is this a time format?
						if (ch == 't' || ch == 'T')
							state = 2;
					}
					break;
				case 2:
					// One more character for time conversion
					s1 += ch;
					state = 0;
					break;
				}
			}

			format = s1.trim();
			unit = s2.trim();
		}
	}

	String getLabel() {
		return label;
	}

	String getFormat() {
		return format;
	}

	String getUnit() {
		return unit;
	}

	String getMapping() {
		return map;
	}

}
