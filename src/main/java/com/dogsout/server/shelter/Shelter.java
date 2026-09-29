package com.dogsout.server.shelter;

/**
 * One dog shelter in the directory: one per Swiss canton, Austrian and German Bundesland.
 *
 * @param latitude        town-level, for sorting by distance only — not an address
 * @param coversNeighbour true where a region has no shelter of its own and the entry is
 *                        its neighbour's (Appenzell Innerrhoden is served from Herisau)
 * @param closedUntil     "YYYY-MM" while the shelter is temporarily closed, else null
 */
public record Shelter(
        String id,
        String country,
        String region,
        String name,
        String town,
        String website,
        double latitude,
        double longitude,
        Boolean coversNeighbour,
        String closedUntil
) {}
