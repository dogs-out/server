package com.dogsout.server;

public final class GeoUtil {

    private static final double EARTH_RADIUS = 6371.0;

    private GeoUtil() {}

    /** Haversine distance in kilometres. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double la1 = Math.toRadians(lat1);
        double la2 = Math.toRadians(lat2);
        double lo1 = Math.toRadians(lon1);
        double lo2 = Math.toRadians(lon2);
        double a = (Math.sin((la2 - la1) / 2) * Math.sin((la2 - la1) / 2)
                + Math.cos(la1) * Math.cos(la2) * Math.sin((lo2 - lo1) / 2) * Math.sin((lo2 - lo1) / 2));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - Math.min(1.0, a)));
        return EARTH_RADIUS * c;
    }
}
