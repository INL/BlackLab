/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utilities for handling dates.
 */
public class DateUtil {

	private DateUtil() {
	}

	/**
	 * Parse a date.
	 *
	 * Dates may be of the following forms: - 1 februari 1980 - 01-02-1980
	 *
	 * @param input
	 *            the date string
	 * @return the Date object
	 * @throws ParseException
	 */
	public static Date parseDate(String input) throws ParseException {
		return parseDate(input, true);
	}

	/**
	 * Parse a date.
	 *
	 * Dates may be of the following forms: - 1 februari 1980 - 01-02-1980 - februari 1980 (if
	 * mustGiveDay == false)
	 *
	 * @param input
	 *            the date string
	 * @param mustGiveDay
	 *            if true, a day component must be given (year/month is not enough)
	 * @return the Date object
	 * @throws ParseException
	 */
	public static Date parseDate(String input, boolean mustGiveDay) throws ParseException {
		Locale locale = LocaleUtil.getDutchLocale();
		try {
			return parseDate(input, locale, "dd MMMM yy");
		} catch (ParseException e) {
			// try next
		}

		if (!mustGiveDay) {
			try {
				return parseDate(input, locale, "MMMM yy");
			} catch (ParseException e) {
				// try next
			}
		}

		try {
			return parseDate(input, locale, "dd-MM-yy");
		} catch (ParseException e) {
			// try next
		}

		throw new ParseException("Cannot parse date", 0);

	}

	/**
	 * Parse a date, according to a locale and a SimpleDateFormat pattern.
	 *
	 * @param input
	 *            the date string
	 * @param locale
	 *            the locale to use (e.g. dutch)
	 * @param pattern
	 *            the SimpleDateFormat pattern, e.g. "dd-MM-yy"
	 * @return the Date object
	 * @throws ParseException
	 */
	public static Date parseDate(String input, Locale locale, final String pattern)
			throws ParseException {
		input = StringUtil.normalizeWhitespace(input); // don't trip over 2 spaces
		DateFormat df = new SimpleDateFormat(pattern, locale);
		return df.parse(input);
	}

	/**
	 * Parse a date, according to a SimpleDateFormat pattern.
	 *
	 * @param input
	 *            the date string
	 * @param pattern
	 *            the SimpleDateFormat pattern, e.g. "dd-MM-yy"
	 * @return the Date object
	 * @throws ParseException
	 */
	public static Date parseDate(String input, final String pattern) throws ParseException {
		Locale locale = LocaleUtil.getDutchLocale();
		DateFormat df = new SimpleDateFormat(pattern, locale); // NOTE: building
																// SimpleDateFormat is really
																// expensive qua CPU/memory! Cache
																// common patterns?
		return df.parse(input);
	}

	static DateFormat dfSortableDateWithDashes = new SimpleDateFormat("yyyy-MM-dd");

	static DateFormat dfSortableDate = new SimpleDateFormat("yyyyMMdd");

	/**
	 * Format a date so it is properly sortable.
	 *
	 * This means the year comes first, then the month, then the day.
	 *
	 * Output is of either of these two forms: - 1980-02-01 - 19800201
	 *
	 * @param date
	 *            the date to format
	 * @param withDashes
	 *            put dashes in the output?
	 * @return the formatted date
	 */
	public static String getSortableDateString(Date date, boolean withDashes) {
		DateFormat df = withDashes ? dfSortableDateWithDashes : dfSortableDate;
		return df.format(date);
	}

	/**
	 * Format a date so it is properly sortable.
	 *
	 * This means the year comes first, then the month, then the day.
	 *
	 * Output is of the form: - 1980-02-01
	 *
	 * @param date
	 *            the date to format
	 * @return the formatted date
	 */
	public static String getSortableDateString(Date date) {
		return getSortableDateString(date, true);
	}

	static DateFormat dfSqlDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * Format a date/time according to the SQL datetime convention.
	 *
	 * @param d
	 *            the date to format
	 * @return a string representation, e.g. "1980-02-01 00:00:00"
	 */
	public static String getSqlDateTimeString(Date d) {
		return dfSqlDateTime.format(d);
	}

	/**
	 * Format a date according to the SQL date convention (YYYY-MM-DD).
	 *
	 * @param date
	 *            the date to format
	 * @return a string representation, e.g. "1980-02-01"
	 */
	public static String getSqlDateString(Date date) {
		return getSortableDateString(date, true);
	}

	static DateFormat dfCondensedDateTime = new SimpleDateFormat("yyyyMMddHHmmss");

	/**
	 * Format a date/time in a compact way.
	 *
	 * @param d
	 *            the date to format
	 * @return a string representation, e.g. "19800201000000"
	 */
	public static String getCondensedDateTimeString(Date d) {
		return dfCondensedDateTime.format(d);
	}

	/**
	 * Get the year from a Date object.
	 *
	 * @param d
	 *            the Date object
	 * @return the year
	 */
	public static int getYear(Date d) {
		return Integer.parseInt(new SimpleDateFormat("yyyy").format(d));
	}

	/**
	 * Get the month from a Date object.
	 *
	 * @param d
	 *            the Date object
	 * @return the month (1-based)
	 */
	public static int getMonth(Date d) {
		return Integer.parseInt(new SimpleDateFormat("MM").format(d));
	}

	/**
	 * Get the day of the month from a Date object.
	 *
	 * @param d
	 *            the Date object
	 * @return day of the month (1-based)
	 */
	public static int getDayOfMonth(Date d) {
		return Integer.parseInt(new SimpleDateFormat("dd").format(d));
	}

	// should be unit tests..
	public static void main(String[] args) throws ParseException {
		final Date date = parseDate("1 februari 2003");
		System.out.println(date);
		System.out.println(parseDate("1 feb 2003"));
		System.out.println(parseDate("1-2-03"));
		System.out.println(parseDate("01-02-03"));
		System.out.println(parseDate("01-02-03?"));
		try {
			System.out.println(parseDate("bla bla bla"));
			throw new RuntimeException("Should have failed");
		} catch (ParseException e) {
			// ok, expected
			System.out.println("Failed as expected");
		}

		System.out.println(getSortableDateString(date));
		System.out.println(getSqlDateTimeString(date));

		SimpleDateFormat df = new SimpleDateFormat("MMMMM", LocaleUtil.getDutchLocale());
		System.out.println("Maand: " + df.format(date));
	}

	/**
	 * Parse a SQL datetime string.
	 *
	 * For example: "1980-02-01 00:00:00"
	 *
	 * @param dateTime
	 *            the datetime string
	 * @return the corresponding Date object
	 * @throws ParseException
	 */
	public static Date parseSqlDateTime(String dateTime) throws ParseException {
		return parseDate(dateTime, "yyyy-MM-dd HH:mm:ss");
	}

	/**
	 * Reverse a numerical date with dashes in between.
	 *
	 * For example, "01-02-1980" will become "1980-02-01".
	 *
	 * @param from
	 *            the date string to reverse
	 * @return the reversed date string
	 */
	public static String reverseDateString(String from) {
		return from.replaceAll("^(\\d+)\\-(\\d+)\\-(\\d+)$", "$3-$2-$1");
	}

	/** Dutch names of the month, for use in getMonthName */
	final static String[] dutchMonthNames = { "januari", "februari", "maart", "april", "mei",
			"juni", "juli", "augustus", "september", "oktober", "november", "december" };

	// TODO: this should be done with date formatting and locales!

	/**
	 * Return the (Dutch) month name
	 *
	 * @param monthNumber
	 *            one-based month number (1=January, ...)
	 * @return the (Dutch) month name
	 */
	public static String getDutchMonthName(int monthNumber) {
		if (monthNumber < 1 || monthNumber > 12)
			throw new RuntimeException("Invalid month number " + monthNumber + " (must be 1-12)");
		return dutchMonthNames[monthNumber - 1];
	}

	final static Locale englishLocale = new Locale("en", "US");

	static DateFormat apacheDateFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z",
			englishLocale);

	/**
	 * Parse a date and time in Apache web server log format (dd/MMM/yyyy:HH:mm:ss Z)
	 *
	 * @param dateStr the date/time string to parse
	 * @return the date and time
	 * @throws ParseException if the date/time couldn't be parsed
	 */
	public static Date parseApacheLogDate(String dateStr) throws ParseException {
		return apacheDateFormat.parse(dateStr);
	}

	static DateFormat dfWeekday = new SimpleDateFormat("EE");

	public static String getWeekdayName(Date date) {
		return dfWeekday.format(date);
	}

	static DateFormat dfYearMonth = new SimpleDateFormat("yyyy-MM");

	/**
	 * Format the current year and month (YYYY-MM).
	 *
	 * @param date the date to format
	 * @return a string representation, e.g. "1980-02"
	 */
	public static String getYearMonthString(Date date) {
		return dfYearMonth.format(date);
	}

	/**
	 * Format the current date and time according to the SQL datetime convention.
	 *
	 * @return a string representation, e.g. "1980-02-01 00:00:00"
	 */
	public static String getSqlDateTimeString() {
		return getSqlDateTimeString(new Date());
	}

}
