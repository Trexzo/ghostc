package dev.ghostfusion.services;

import dev.ghostfusion.core.ModuleRegistry;

final class ServiceValidation {
    private ServiceValidation() { }
    static void owner(String id) { ModuleRegistry.validateId(id); }
    static void request(String id, long tick, int ttl, long generation) {
        owner(id);
        if (tick < 0 || ttl < 1 || ttl > 200 || generation < 0 || tick > Long.MAX_VALUE - ttl)
            throw new IllegalArgumentException("request time");
    }
}
