/*
 * Copyright 2014 Anton Tananaev (anton.tananaev@gmail.com)
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
 */
package org.traccar.protocol;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.traccar.BaseProtocolDecoder;
import org.traccar.ServerManager;
import org.traccar.helper.Log;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class AspicoreProtocolDecoder extends BaseProtocolDecoder {

    private Long deviceId;

    public AspicoreProtocolDecoder(ServerManager serverManager) {
        super(serverManager);
    }

    static private Pattern patternIMEI = Pattern.compile(
            "IMEI\\s+(\\d+)");                   // IMEI
    private static final Pattern patternGPRMC = Pattern.compile(
            "\\$GPRMC," +
            "(\\d{2})(\\d{2})(\\d{2})\\.?\\d*," + // Time (HHMMSS.SSS)
            "([AV])," +                    // Validity
            "(\\d{2})(\\d{2}\\.\\d+)," +   // Latitude (DDMM.MMMM)
            "([NS])," +
            "(\\d{3})(\\d{2}\\.\\d+)," +   // Longitude (DDDMM.MMMM)
            "([EW])," +
            "(\\d+\\.?\\d*)?," +           // Speed
            "(\\d+\\.?\\d*)?," +           // Course
            "(\\d{2})(\\d{2})(\\d{2})" +   // Date (DDMMYY)
            ".+");
    private static final Pattern patternGPGGA = Pattern.compile(
            "\\$GPGGA," +
            "(\\d{2})(\\d{2})(\\d{2})\\.?\\d*," + // Time
            "(\\d{2})(\\d{2}\\.\\d+)," +   // Latitude
            "([NS])," +
            "(\\d{3})(\\d{2}\\.\\d+)," +   // Longitude
            "([EW])," +
            "([012])," +                   // GPS fix
            "\\d+," +                      // Number of sattelites
            "\\d+\\.?\\d*," +              // HDOP
            "(-?\\d+\\.?\\d*)," +          // Altitude above geoid (MSL)
            "M," +                         // meters
            "(-?\\d+\\.?\\d*)," +          // The difference between
                                           // WGS-84 ellipsoid and geoid
            "M," +
            "\\d*," +                      // DGPS age
            "\\d*" +                       // DGPS station ID
            "\\*[\\dA-F]+");                 // Checksum

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        String sentence = (String) msg;

        if (sentence.startsWith("IMEI")) {
            // Parse message
            Matcher parser = patternIMEI.matcher(sentence);
            if (!parser.matches()) {
                return null;
            }
            String imei = parser.group(1);
        try {
            deviceId = getDataManager().getDeviceByImei(imei).getId();
        } catch(Exception error) {
            return null;
        }
        }
        // Location
        else if (sentence.startsWith("$GPRMC") && deviceId != null) {

            // Parse message
            Matcher parser = patternGPRMC.matcher(sentence);
            if (!parser.matches()) {
                return null;
            }

            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("aspicore");
            position.setDeviceId(deviceId);

            Integer index = 1;

            // Time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            time.clear();
            time.set(Calendar.HOUR, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));

            // Validity
            position.setValid(parser.group(index++).compareTo("A") == 0);

            // Latitude
            Double latitude = Double.valueOf(parser.group(index++));
            latitude += Double.valueOf(parser.group(index++)) / 60;
            if (parser.group(index++).compareTo("S") == 0) latitude = -latitude;
            position.setLatitude(latitude);

            // Longitude
            Double longitude = Double.valueOf(parser.group(index++));
            longitude += Double.valueOf(parser.group(index++)) / 60;
            if (parser.group(index++).compareTo("W") == 0) longitude = -longitude;
            position.setLongitude(longitude);

            // Speed
            String speed = parser.group(index++);
            if (speed != null) {
                position.setSpeed(Double.valueOf(speed));
            } else {
                position.setSpeed(0.0);
            }

            // Course
            String course = parser.group(index++);
            if (course != null) {
                position.setCourse(Double.valueOf(course));
            } else {
                position.setCourse(0.0);
            }

            // Date
            time.set(Calendar.DAY_OF_MONTH, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MONTH, Integer.valueOf(parser.group(index++)) - 1);
            time.set(Calendar.YEAR, 2000 + Integer.valueOf(parser.group(index++)));
            position.setTime(time.getTime());

            // Altitude
            position.setAltitude(0.0);

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        // Location
        else if (sentence.startsWith("$GPGGA") && deviceId != null) {

            // Parse message
            Matcher parser = patternGPGGA.matcher(sentence);
            if (!parser.matches()) {
                return null;
            }

            // Create new position
            Position position = new Position();
            ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("aspicore");
            position.setDeviceId(deviceId);

            Integer index = 1;

            // Time
            Calendar time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            Integer day   = time.get(Calendar.DAY_OF_MONTH);
            Integer month = time.get(Calendar.MONTH);
            Integer year  = time.get(Calendar.YEAR);
            time.clear();
            time.set(Calendar.HOUR, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MINUTE, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.SECOND, Integer.valueOf(parser.group(index++)));
            time.set(Calendar.MILLISECOND, 0);
            time.set(Calendar.DAY_OF_MONTH, day);
            time.set(Calendar.MONTH, month);
            time.set(Calendar.YEAR, year);
            position.setTime(time.getTime());

            // Latitude
            Double latitude = Double.valueOf(parser.group(index++));
            latitude += Double.valueOf(parser.group(index++)) / 60;
            if (parser.group(index++).compareTo("S") == 0) latitude = -latitude;
            position.setLatitude(latitude);

            // Longitude
            Double longitude = Double.valueOf(parser.group(index++));
            longitude += Double.valueOf(parser.group(index++)) / 60;
            if (parser.group(index++).compareTo("W") == 0) longitude = -longitude;
            position.setLongitude(longitude);

            // GPS fix
            position.setValid(parser.group(index++).compareTo("0") != 0);

            // Altitude
            Double altitude = Double.valueOf(parser.group(index++));
            altitude += Double.valueOf(parser.group(index++));
            position.setAltitude(altitude);
            
            // Speed
            position.setSpeed(0.0);
            
            // Course
            position.setCourse(0.0);

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        return null;
    }

}
