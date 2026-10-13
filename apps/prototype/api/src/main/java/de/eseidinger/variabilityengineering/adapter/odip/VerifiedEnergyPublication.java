package de.eseidinger.variabilityengineering.adapter.odip;

import de.eseidinger.variabilityengineering.energy.EnergyCatalog;
import de.eseidinger.variabilityengineering.energy.EnergyProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A publication is exposed only after all five immutable artifacts pass verification. */
public final class VerifiedEnergyPublication {
    private final byte[] manifestBytes;
    private final Map<String, byte[]> artifacts;
    private final String manifestSha256;
    private final EnergyCatalog catalog;
    private final EnergyProfile profile;
    private final List<Warning> warnings;

    public record Warning(String code, String message) { }

    VerifiedEnergyPublication(byte[] manifestBytes, Map<String, byte[]> artifacts, String manifestSha256,
                              EnergyCatalog catalog, EnergyProfile profile, List<Warning> warnings) {
        this.manifestBytes = manifestBytes.clone();
        var copy = new LinkedHashMap<String, byte[]>();
        artifacts.forEach((id, bytes) -> copy.put(id, bytes.clone()));
        this.artifacts = Map.copyOf(copy);
        this.manifestSha256 = manifestSha256;
        this.catalog = catalog;
        this.profile = profile;
        this.warnings = List.copyOf(warnings);
    }

    public byte[] manifestBytes() { return manifestBytes.clone(); }
    public Map<String, byte[]> artifactBytes() {
        var copy = new LinkedHashMap<String, byte[]>();
        artifacts.forEach((id, bytes) -> copy.put(id, bytes.clone()));
        return Map.copyOf(copy);
    }
    public String manifestSha256() { return manifestSha256; }
    public EnergyCatalog catalog() { return catalog; }
    public EnergyProfile profile() { return profile; }
    public List<Warning> warnings() { return warnings; }
}
