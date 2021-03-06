/*
 *
 *  Managed Data Structures
 *  Copyright © 2016 Hewlett Packard Enterprise Development Company LP.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  As an exception, the copyright holders of this Library grant you permission
 *  to (i) compile an Application with the Library, and (ii) distribute the 
 *  Application containing code generated by the Library and added to the 
 *  Application during this compilation process under terms of your choice, 
 *  provided you also meet the terms and conditions of the Application license.
 *
 */

package com.hpl.erk;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hpl.erk.formatters.SeqFormatter;
import com.hpl.erk.impl_helper.CompareToImpl;
import com.hpl.erk.impl_helper.EqualsImpl;
import com.hpl.erk.util.NumUtils;

/**
 * Represents a duration of elapsed time.  Typically created by calling {@link Duration#of(long, TimeUnit)} or
 * {@link Duration#of(double, TimeUnit)}. Durations are considered equal if they represent the same span of time,
 * regardless of units, and equal durations have identical hash codes.  Durations sort shortest to longest.
 * Negative durations are allowed.  Duration objects are immutable.
 * 
 * @author Evan Kirshenbaum
 *
 */
public class Duration implements Comparable<Duration> {
  private static final TimeUnit[] unitValues = TimeUnit.values();
  /**
   * A Duration object representing zero duration (in nanoseconds).
   */
  public static final Duration ZERO = new Duration(0, TimeUnit.NANOSECONDS);
  /**
   * The count, in {@link #unit}s of the duration.
   */
  public final long count;
  /**
   * The time unit used to interpret the {@link #count}.
   */
  public final TimeUnit unit;
  
  /**
   * A new duration object in the given units.
   * @param n a count in the time unit
   * @param unit a time unit
   */
  public Duration(long n, TimeUnit unit) {
    this.count = n;
    this.unit = unit;
  }
  
  /**
   * @param otherUnit a time unit
   * @return
   * a Duration object equivalent to this one in the given units.  If the given units are
   * smaller than in this one, the value may lose precision.  Equivalent to 
   * <code>Duration.of({@link #in(otherUnit)}, otherUnit)</code>.
   */
  public Duration as(TimeUnit otherUnit) {
    return new Duration(in(otherUnit), otherUnit);
  }

  /**
   * @param otherUnit a time unit
   * @return
   * the numeric value of this Duration object in the given units.  If the given units are
   * smaller than in this one, the value may lose precision.  Conversion is done by
   * {@link TimeUnit#convert(long, TimeUnit)}, so 
   * <code>Duration.of(65, TimeUnit.SECONDS).in(TimeUnit.MINUTES)</code> will return <code>1</code>.
  */
  public long in(TimeUnit otherUnit) {
    return otherUnit.convert(count, unit);
  }
  
  /**
   * @param u a time unit
   * @return this duration as a real-valued number of the given units.
   */
  public double asFractional(TimeUnit u) {
    double inNanos = in(TimeUnit.NANOSECONDS);
    double uInNanos = TimeUnit.NANOSECONDS.convert(1, u);
    return inNanos/uInNanos;
  }
  
  /**
   * 
   * @return a duration in the coarsest possible unit capable of exactly representing this
   * duration.  For example, <code>Duration.parseDuration("7,200 s").reduce()</code> will return
   * a duration equivalent to <code>Duration.of(2, TimeUnit.HOURS)</code>
   */
  public Duration reduced() {
    TimeUnit bestUnit = unit;
    long bestN = count;
    for (int i=unit.ordinal()+1; i<unitValues.length; i++) {
      TimeUnit u = unitValues[i];
      long conversion = bestUnit.convert(1, u);
      if (bestN % conversion != 0) {
        break;
      }
      bestUnit = u;
      bestN /= conversion;
    }
    if (bestUnit == unit) {
      return this;
    }
    return Duration.of(bestN, bestUnit);
  }

  /**
   * @param n a count in the given unit
   * @param unit a time unit
   * @return
   * a Duration object (probably, but not guaranteed to be unique) representing the given span
   * of the given units. 
   */
  public static Duration of(long n, TimeUnit unit) {
    return new Duration(n, unit);
  }
  
  /**
   * @param n a length in the given unit
   * @param unit a time unit
   * @return
   * a Duration object (probably, but not guaranteed to be unique) representing, as exactly as possible,
   * the given span in the given units.  If the span is not a whole number, successively finer-grained
   * units are tried until an exact match is found or nanoseconds are reached.
   */
  public static Duration of(double n, TimeUnit unit) {
    double whole = Math.floor(n);
    double frac = n-whole;
    while (frac != 0 && unit != TimeUnit.NANOSECONDS) {
      TimeUnit nextUnit = unitValues[unit.ordinal()-1];
      long scale = nextUnit.convert(1, unit);
      n *= scale;
      whole = Math.floor(n);
      frac = n-whole;
      unit = nextUnit;
    }
    return Duration.of((long)whole, unit);
  }
  

  /**
   * @param start a timestamp, as returned by {@link System#currentTimeMillis()}.
   * @return a Duration object equal to the number of milliseconds since the given timestamp.
   */
  public static Duration since(long start) {
    long now = System.currentTimeMillis();
    return Duration.of(now-start, TimeUnit.MILLISECONDS);
  }
  
  /**
   * @param other a timestamp
   * @return a Duration object equal to the sum of this timestamp and the given timestamp.  The 
   * units of the returned object will be the more finer-grained of the two timestamps.
   */
  public Duration plus(Duration other) {
    if (other.count == 0) {
      return this;
    }
    if (count == 0) {
      return other;
    }
    if (unit == other.unit) {
      return new Duration(count+other.count, unit);
    }
    if (unit.compareTo(other.unit) < 0) {
      return new Duration(count+other.in(unit), unit);
    }
    return new Duration(in(other.unit)+other.count, other.unit);
  }
  
  /**
   * @param other a timestamp
   * @return a Duration object equal to the difference between this timestamp and the given timestamp.
   * The 
   * units of the returned object will be the more finer-grained of the two timestamps.
   */
  public Duration minus(Duration other) {
    if (other.count == 0) {
      return this;
    }
    if (count == 0) {
      return new Duration(-other.count, unit);
    }
    if (unit == other.unit) {
      return new Duration(count+other.count, unit);
    }
    if (unit.compareTo(other.unit) < 0) {
      return new Duration(count-other.in(unit), unit);
    }
    return new Duration(-in(other.unit)+other.count, other.unit);
  }
  
  /**
   * 
   * @param multiplier
   * @return a Duration object equal to the multiplier times the current object.
   */
  public Duration times(long multiplier) {
    if (count == 1) {
      return this;
    }
    if (count == 0) {
      return ZERO;
    }
    return Duration.of(count*multiplier, unit);
  }

  /**
   * 
   * @param multiplier
   * @return a Duration object equal to the multiplier times the current object.
   */
  public Duration times(double multiplier) {
    if (count == 1) {
      return this;
    }
    if (count == 0) {
      return ZERO;
    }
    return Duration.of(count*multiplier, unit);
  }

  @Override
  public int compareTo(Duration o) {
    CompareToImpl<Duration> cti = CompareToImpl.nullsLast(this, o);
    if (!cti.isDone()) {
      if (unit == o.unit) {
        cti.compare(count, o.count);
      } else {
        long myNanos = in(TimeUnit.NANOSECONDS);
        long hisNanos = o.in(TimeUnit.NANOSECONDS);
        return Long.compare(myNanos, hisNanos);
      }
    }
    return cti.value();
  }
  
  /**
   * 
   * @param o another duration
   * @return <code>true</code> if this duration is longer than the other duration.
   */
  public boolean longerThan(Duration o) {
    return compareTo(o) > 0;
  }
  
  @Override
  public boolean equals(Object obj) {
    return EqualsImpl.check(this, obj, Duration.class);
  }
  
  @Override
  public int hashCode() {
    return NumUtils.longHash(in(TimeUnit.NANOSECONDS));
  }
  
  @Override
  public String toString() {
    return formatNumberUnit();
  }

  /**
   * 
   * @return a string representing the duration in Number-Unit format (see {@link #parseNumberUnit(String)}).
   */
  public String formatNumberUnit() {
    String uString = unitAbbrev(unit, count != 1);
    return String.format("%s %s", IOUtils.formatGrouped(count, "_"), uString);
  }

  /**
   * 
   * @param reduce if <code>true</code>, the duration is first reduced before being formatted.
   * @return a string representing the duration in Number-Unit format (see {@link #parseNumberUnit(String)}).
   */
  public String formatNumberUnit(boolean reduce) {
    return reduce ? reduced().formatNumberUnit() : formatNumberUnit();
  }
  
  private static String unitAbbrev(TimeUnit unit, boolean plural) {
    switch (unit) {
    case NANOSECONDS:
      return "ns";
    case MICROSECONDS:
      return "us";
    case MILLISECONDS:
      return "ms";
    case SECONDS:
      return "sec";
    case MINUTES:
      return "min";
    case HOURS:
      return "hr";
    case DAYS:
      return plural ? "days" : "day";
    }
    throw new IllegalStateException();
  }
  
  /**
   * 
   * @param precision the number of places past the decimal point
   * @return a string representing the duration in HMS format (see {@link #parseHMS(String)}),
   * with fractional seconds given to the desired number of places.
   */
  public String formatHMS(int precision) {
    if (precision < 0) {
      precision = 0;
    }
    if (precision > 9) {
      precision = 9;
    }
    long remaining = in(TimeUnit.NANOSECONDS);
    long hours = TimeUnit.HOURS.convert(remaining, TimeUnit.NANOSECONDS);
    remaining -= TimeUnit.NANOSECONDS.convert(hours, TimeUnit.HOURS);
    long min = TimeUnit.MINUTES.convert(remaining, TimeUnit.NANOSECONDS);
    remaining -= TimeUnit.NANOSECONDS.convert(min, TimeUnit.MINUTES);
    long sec = TimeUnit.SECONDS.convert(remaining, TimeUnit.NANOSECONDS);
    String frac = "";
    if (precision > 0) {
      remaining -= TimeUnit.NANOSECONDS.convert(min, TimeUnit.SECONDS);
      long ms = TimeUnit.MILLISECONDS.convert(remaining, TimeUnit.NANOSECONDS);
      remaining -= TimeUnit.NANOSECONDS.convert(min, TimeUnit.MILLISECONDS);
      long us = TimeUnit.MICROSECONDS.convert(remaining, TimeUnit.NANOSECONDS);
      remaining -= TimeUnit.NANOSECONDS.convert(min, TimeUnit.MICROSECONDS);
      long ns = remaining;
      frac = String.format(".%03d%03d%03d", ms, us, ns);
      frac = frac.substring(0, precision+1);
    }
    if (hours > 0) {
      return String.format("%d:%02d:%02d%s", hours, min, sec, frac);
    } else {
      return String.format("%d:%02d%s", min, sec, frac);
    }
  }
  
  /**
   * 
   * @param precision a unit representing the desired precision, which should be at least TimeUnit.SECONDS.
   * @return a string representing the duration in HMS format (see {@link #parseHMS(String)}),
   * with fractional seconds given to the desired number of places.
   */
  public String formatHMS(TimeUnit precision) {
    return formatHMS(3*(TimeUnit.SECONDS.ordinal()-precision.ordinal()));
  }
  
  
  private final static Map<String, TimeUnit> units = new HashMap<>();
  static {
    units.put("d", TimeUnit.DAYS);
    units.put("dy", TimeUnit.DAYS);
    units.put("day", TimeUnit.DAYS);
    units.put("days", TimeUnit.DAYS);
    units.put("h", TimeUnit.HOURS);
    units.put("hr", TimeUnit.HOURS);
    units.put("hrs", TimeUnit.HOURS);
    units.put("hour", TimeUnit.HOURS);
    units.put("hours", TimeUnit.HOURS);
    units.put("m", TimeUnit.MINUTES);
    units.put("min", TimeUnit.MINUTES);
    units.put("mins", TimeUnit.MINUTES);
    units.put("minute", TimeUnit.MINUTES);
    units.put("minutes", TimeUnit.MINUTES);
    units.put("s", TimeUnit.SECONDS);
    units.put("sec", TimeUnit.SECONDS);
    units.put("secs", TimeUnit.SECONDS);
    units.put("second", TimeUnit.SECONDS);
    units.put("seconds", TimeUnit.SECONDS);
    units.put("ms", TimeUnit.MILLISECONDS);
    units.put("millisecond", TimeUnit.MILLISECONDS);
    units.put("milliseconds", TimeUnit.MILLISECONDS);
    units.put("us", TimeUnit.MICROSECONDS);
    units.put("μs", TimeUnit.MICROSECONDS);
    units.put("microsecond", TimeUnit.MICROSECONDS);
    units.put("microseconds", TimeUnit.MICROSECONDS);
    units.put("ns", TimeUnit.NANOSECONDS);
    units.put("nanosecond", TimeUnit.NANOSECONDS);
    units.put("nanoseconds", TimeUnit.NANOSECONDS);
  }

  final static Pattern hmsPat = Pattern.compile("(?:(\\d+):)?(\\d+):(\\d+)(\\.[\\d_]+(?<!_))?");
  final static Pattern nuPat = Pattern.compile("(\\d[\\d_,]*(?<![_,])(?:\\.[\\d_]+(?<!_))?)\\s*" 
                                               +SeqFormatter.parenList().sep("|").format(units.keySet()));
  
  public static Pattern INPUT_PATTERN = Pattern.compile("("+hmsPat.pattern()+"|"+nuPat.pattern()+")", Pattern.CASE_INSENSITIVE);

  /**
   * Parse a duration either in HMS format (e.g. "1:04:02.23" or "2:00") or Number-Unit format (e.g. "1 hour" or "3.5 ms").
   * Tries both {@link #parseHMS(String)} and {@link #parseNumberUnit(String)}.
   * @param s a string representing a duration
   * @return the duration represented by the string
   * @throws DurationFormatException if the string cannot be parse.
   */
  public static Duration parseDuration(String s) throws DurationFormatException {
    Duration d = parseHMS(s);
    if (d != null) {
      return d;
    }
    d = parseNumberUnit(s);
    if (d != null) {
      return d;
    }
    throw DurationFormatException.forInputString(s);
  }
  
  /**
   * Parse a duration in HMS format (e.g. "1:04:02.23" or "2:00"). At least one colon must be present, and durations
   * with a single colon are taken to represent minutes and seconds (i.e., "1:30" is one minute and thirty seconds).
   * Fractions of a second may be specified following a decimal point, and precision finer than a nanosecond is ignored.
   * In the fractional part, underscores may be used to improve readability.
   * @param s a string representing the duration in HMS format
   * @return the duration represented by the string or <code>null</code> if the string does not match the pattern
   */
  public static Duration parseHMS(String s) {
    Matcher m = hmsPat.matcher(s);
    if (!m.matches()) {
      return null;
    }
    Duration d = Duration.ZERO;
    if (m.group(1) != null) {
      d = d.plus(Duration.of(Integer.parseInt(m.group(1)), TimeUnit.HOURS));
    }
    d = d.plus(Duration.of(Integer.parseInt(m.group(2)), TimeUnit.MINUTES));
    d = d.plus(Duration.of(Integer.parseInt(m.group(3)), TimeUnit.SECONDS));
    String fracStr = m.group(4);
    if (fracStr != null) {
      fracStr = fracStr.replace("_", "");
      int len = fracStr.length();
      fracStr = (fracStr+"000000000").substring(0,9);
      d = d.plus(Duration.of(Integer.parseInt(fracStr.substring(0,3)), TimeUnit.MILLISECONDS));
      if (len > 3) {
        d = d.plus(Duration.of(Integer.parseInt(fracStr.substring(3,6)), TimeUnit.MICROSECONDS));
        if (len > 6) {
          d = d.plus(Duration.of(Integer.parseInt(fracStr.substring(6,9)), TimeUnit.NANOSECONDS));
        }
      }
    }
    return d;
  }
  
  /**
   * Parse a duration in Number-Unit format, as a number followed by a unit (e.g. "1 hour" or "3.5 ms").
   * Spaces between the number and unit are optional.  The number may have a decimal point.  Commas and underscores
   * may be used to the left of the decimal point, and underscores may be used to the right of the decimal point, to
   * improve readability.
   * <p/>
   * The following units are recognized:
   * <ul>
   * <li>For TimeUnit.DAYS: d, dy, day[s]</li>
   * <li>For TimeUnit.HOURS: h, hr[s], hour[s]</li>
   * <li>For TimeUnit.MINUTES: m, min[s], minute[s]</li>
   * <li>For TimeUnit.SECONDS: s, sec[s], second[s]</li>
   * <li>For TimeUnit.MILLISECONDS: ms, millisecond[s]</li>
   * <li>For TimeUnit.MICROSECONDS: us, μs, microsecond[s]</li>
   * <li>For TimeUnit.NANOSECONDS: ns, nanosecond[s]</li>
   * </ul>
   * 
  */
  public static Duration parseNumberUnit(String s) {
    Matcher m = nuPat.matcher(s);
    if (!m.matches()) {
      return null;
    }
    String num = m.group(1);
    num = num.replace("_", "");
    num = num.replace(",", "");
    double n = Double.parseDouble(num);
    TimeUnit unit = units.get(m.group(2).toLowerCase());
    return Duration.of(n, unit);
  }

  
  public static void main(String[] args) {
    Duration d = Duration.of(1.0/7, TimeUnit.HOURS);
    System.out.format("%s%n", d);
    System.out.format("%s%n", d.as(TimeUnit.MINUTES));
    System.out.format("%s%n", Duration.parseDuration("3:12"));
    System.out.format("%s%n", Duration.parseDuration("3:00"));
    System.out.format("%s%n", Duration.parseDuration("20 sec"));
    System.out.format("%s%n", Duration.parseDuration("3.5 ms"));
    System.out.format("%s%n", Duration.of(90, TimeUnit.MINUTES).asFractional(TimeUnit.HOURS));
    System.out.format("%s%n", Duration.of(90, TimeUnit.MINUTES).asFractional(TimeUnit.DAYS));
    System.out.format("%s%n", Duration.of(65, TimeUnit.SECONDS).in(TimeUnit.MINUTES));
    System.out.format("%s%n", Duration.of(7_200, TimeUnit.SECONDS).reduced());
    System.out.format("%s%n", d.formatHMS(TimeUnit.MICROSECONDS));
    System.out.format("%s%n", d.formatHMS(TimeUnit.SECONDS));
    System.out.format("%s%n", Duration.of(24, TimeUnit.HOURS).formatNumberUnit(true));
  }

}
