package org.openhomeo.core.model;

import java.util.Arrays;
import java.util.List;

/**
 * Classical Homeopathic Constitutional Types and Diatheses.
 * Sourced from classical Hahnemannian, Grauvogl, and Kentian taxonomy.
 */
public enum HomeopathicConstitution {
    CARBONIC("Carbonic (Calcarea carb type - Chilly, fair, slow, robust/flabby, sluggish metabolism)"),
    PHOSPHORIC("Phosphoric (Phosphorus type - Sensitive, tall, slender, reactive, open, hyper-excitable)"),
    FLUORIC("Fluoric (Calcarea fluor type - Asymmetric, joint hypermobility, venous varices, indurations)"),
    SULPHURIC("Sulphuric (Sulphur type - Lean or plethoric, heat intolerance, standing agg., red orifices)"),
    NITRIC("Nitric (Nitric acid type - Dark complexion, rigid fiber, irritable, sensitive to noise/pain)"),
    HYDROGENOID("Grauvogl's Hydrogenoid (Water retention, agg. damp/cold/rain, chilly, sycotic diathesis)"),
    OXYGENOID("Grauvogl's Oxygenoid (Hyper-oxidation, rapid destruction, ravenous hunger with emaciation)"),
    CARBO_NITROGENOID("Grauvogl's Carbo-Nitrogenoid (Defective oxidation, venous congestion, gouty, sluggish elimination)"),
    PSORIC("Psoric Constitution (Skin eruptions, hypersensitivity, pruritus, variable thermals)"),
    SYCOTIC("Sycotic Constitution (Hypertrophy, pelvic catarrhs, fleshy warts/moles, damp agg.)"),
    SYPHILITIC("Syphilitic Constitution (Destructive ulcerations, nighttime bone pains, mental hopelessness)"),
    TUBERCULAR("Tubercular / Scrofulous (Rapidly shifting symptoms, respiratory fragility, wanderlust)"),
    SANGUINE("Sanguine Temperament (Plethoric, energetic, warm-blooded, active circulation)"),
    CHOLERIC_BILIOUS("Choleric / Bilious Temperament (Ambitious, easily provoked to anger, hepatic complaints)"),
    MELANCHOLIC("Melancholic Temperament (Introspective, brooding, sedentary, depressed, chilly)"),
    PHLEGMATIC_LYMPHATIC("Phlegmatic / Lymphatic Temperament (Calm, torpid, slow reaction, pale, moist)"),
    NERVOUS("Nervous Temperament (Quick, excitable, acute senses, insomnia, sudden spasms)");

    private final String description;

    HomeopathicConstitution(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }

    public static List<String> getAllDescriptions() {
        return Arrays.stream(values())
                .map(HomeopathicConstitution::getDescription)
                .toList();
    }
}
