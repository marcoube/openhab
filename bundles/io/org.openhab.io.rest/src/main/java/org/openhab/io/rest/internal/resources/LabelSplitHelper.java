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
					if (ch == '%') {
						// %% is not considered part of the format -
						// it's the "unit"
						state = 0;
						break;
					}
					// Concatenate here - this will remove the double %
					s2 += ch;
					if (conversions.indexOf(ch) != -1) {
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

	LabelSplitHelper(String newLabel, String newFormat, String newUnit,
			String newMap) {
		label = newLabel;
		format = newFormat;
		unit = newUnit;
		map = newMap;
	}

	void setLabel(String newLabel) {
		label = newLabel;
	}

	void setFormat(String newFormat) {
		format = newFormat;
	}

	void setUnit(String newUnit) {
		unit = newUnit;
	}

	void setMapping(String newMap) {
		map = newMap;
	}

	String getLabelString() {
		String config = "";

		// Ensure everything is a string
		if(label == null)
			label = "";
		if(format == null)
			format = "";
		if(unit == null)
			unit = "";
		if(map == null)
			map = "";
		
		// Resolve double %% in unit
		String unitOut = "";
		for(int c = 0; c < unit.length(); c++) {
			unitOut += unit.charAt(c);
			if(unit.charAt(c) == '%')
				unitOut += '%';
		}
		unit = unitOut;

		// Concatenate it all together!
		config += label;
		if(!format.isEmpty() || !unit.isEmpty() || !map.isEmpty()) {
			config += " [";
			if (!map.isEmpty())
				config += "MAP(" + map + "):";
			if(!format.isEmpty())
				config += format;
			if(!unit.isEmpty())
				config += " " + unit;
			config += "]";
		}

		return config;
	}
}
