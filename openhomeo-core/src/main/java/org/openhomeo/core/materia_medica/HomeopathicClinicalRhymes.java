package org.openhomeo.core.materia_medica;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Clinical Rhymes and Keynote Verses for Homeopathic Remedy Selection.
 * Preserves the classical pedagogical and clinical mnemonic tradition of
 * Dr. E. B. Nash, Dr. H. C. Allen, and Indian Homeopathic Masters.
 */
public class HomeopathicClinicalRhymes {

    private static final Map<String, String> RHYMES = new HashMap<>();

    static {
        // Classical Polychrests & Major Remedies
        put("acon", "Aconitum napellus",
                "Sudden storm of fever high,\n" +
                "Restless anguish, fear to die;\n" +
                "Skin is burning, hot and dry,\n" +
                "Aconite calms the tempest cry.");

        put("aesc", "Aesculus hippocastanum",
                "Rectum full of sticks and wood,\n" +
                "Severe backache where he stood;\n" +
                "Purple piles that burn and bleed,\n" +
                "Aesculus is the friend in need.");

        put("aeth", "Aethusa cynapium",
                "Intolerant of milk and food,\n" +
                "Curd-like vomiting in weakly brood;\n" +
                "Stupor and anguish on the face,\n" +
                "Aethusa restores the baby's grace.");

        put("agar", "Agaricus muscarius",
                "Twitching muscles, jerking toes,\n" +
                "Diagonal chorea comes and goes;\n" +
                "Chilblains itch in winter cold,\n" +
                "Agaricus stays the trembling hold.");

        put("agn", "Agnus castus",
                "Premature old age, vitality gone,\n" +
                "Sexual organs flaccid and wan;\n" +
                "Melancholy brooding over the past,\n" +
                "Agnus castus brings vigor at last.");

        put("all-c", "Allium cepa",
                "Eyes are running, water clear,\n" +
                "Nose is burning, dropped like tear;\n" +
                "Acrid coryza, cough in throat,\n" +
                "Allium cepa strikes the note.");

        put("aloe", "Aloe socotrina",
                "Early morning sudden rush,\n" +
                "Sphincter weak with sputtering gush;\n" +
                "Lumpy jelly, portal weight,\n" +
                "Aloe seals the open gate.");

        put("alum", "Alumina",
                "Dryness deep from head to toe,\n" +
                "Even soft stool passes slow;\n" +
                "Cannot walk with eyes shut tight,\n" +
                "Alumina brings the strength upright.");

        put("ambr", "Ambra grisea",
                "Shy and nervous, coughs in crowds,\n" +
                "Fainting fits and music shrouds;\n" +
                "Ranula large beneath the tongue,\n" +
                "Ambra heals both old and young.");

        put("am-c", "Ammonium carbonicum",
                "Stoppage of nose in nighttime sleep,\n" +
                "Heart failure where the breath is deep;\n" +
                "Chilly, tired, blood runs blue,\n" +
                "Ammon carb revives him through.");

        put("anac", "Anacardium orientale",
                "Two wills striving, black and white,\n" +
                "Eating brings a sweet respite;\n" +
                "Profane and cursed, memory gone,\n" +
                "Anacardium brings the dawn.");

        put("ant-c", "Antimonium crudum",
                "Thick white tongue like snowy sheet,\n" +
                "Sentimental, loves to eat;\n" +
                "Cannot bear to be looked upon,\n" +
                "Antim crud makes sorrow gone.");

        put("ant-t", "Antimonium tartaricum",
                "Rattling chest with hollow sound,\n" +
                "Too weak to cough the mucus round;\n" +
                "Drowsy, sweaty, breathing tight,\n" +
                "Tartar emetic brings back light.");

        put("apis", "Apis mellifica",
                "Stinging, burning, rosy swell,\n" +
                "Thirstless though in fever's cell;\n" +
                "Dreads the heat and screams in night,\n" +
                "Apis puts the sting to flight.");

        put("arg-m", "Argentum metallicum",
                "Larynx scraped and voice undone,\n" +
                "Singers' aphonia has begun;\n" +
                "Cartilage caries, ovary sore,\n" +
                "Silver metal heals the core.");

        put("arg-n", "Argentum nitricum",
                "Craves for sweets, anticipation fear,\n" +
                "Time flies slow when crowds appear;\n" +
                "Splinter sticking in the throat,\n" +
                "Arg nit anchors the nervous boat.");

        put("arn", "Arnica montana",
                "Bed feels hard where'er he lies,\n" +
                "'I am well!' the patient cries;\n" +
                "Bruised all over, black and blue,\n" +
                "Arnica brings the healing true.");

        put("ars", "Arsenicum album",
                "Midnight anguish, burning pain,\n" +
                "Thirsty sips, but seeks in vain;\n" +
                "Better by heat, but prostrate, chill,\n" +
                "Arsenic cures the burning ill.");

        put("aur", "Aurum metallicum",
                "Deepest sorrow, suicidal gloom,\n" +
                "Seeking death within the tomb;\n" +
                "Bone pains through the nightly hour,\n" +
                "Gold restores the vital power.");

        put("bapt", "Baptisia tinctoria",
                "Besotted face and stupor deep,\n" +
                "Feels all scattered in his sleep;\n" +
                "Foul discharges, painless throat,\n" +
                "Baptisia stays the toxic boat.");

        put("bar-c", "Baryta carbonica",
                "Dwarfed in body, dwarfed in mind,\n" +
                "Bashful with the human kind;\n" +
                "Swollen tonsils, memory slow,\n" +
                "Baryta carb helps children grow.");

        put("bell", "Belladonna",
                "Sudden onset, burning red,\n" +
                "Throbbing carotids in the head;\n" +
                "Dilated pupils, wild and fierce,\n" +
                "Belladonna's charms the fever pierce.");

        put("bell-p", "Bellis perennis",
                "Deep pelvic trauma after birth,\n" +
                "Cold bath taken in overheated mirth;\n" +
                "Bruised muscle fibers aching deep,\n" +
                "Bellis perennis grants sweet sleep.");

        put("berb", "Berberis vulgaris",
                "Radiating pains that start and go,\n" +
                "Bubbling kidneys, urine slow;\n" +
                "Red sand gravel, gallstone cry,\n" +
                "Berberis bids the colic fly.");

        put("bism", "Bismuthum subnitricum",
                "Water vomited soon as it hits,\n" +
                "Food retained in stomach sits;\n" +
                "Desires company, cannot bear alone,\n" +
                "Bismuth stills the gastric moan.");

        put("borx", "Borax veneta",
                "Dread of downward motion keen,\n" +
                "Aphthae white with bloody sheen;\n" +
                "Startled by the slightest sound,\n" +
                "In Borax healing peace is found.");

        put("bov", "Bovista gigantea",
                "Awkward fingers, dropping things,\n" +
                "Intermenstrual flow it brings;\n" +
                "Puffiness of hands and skin,\n" +
                "Bovista heals the flux within.");

        put("bry", "Bryonia alba",
                "Stitching pains by motion bred,\n" +
                "Wants to lie on aching bed;\n" +
                "Firm hard pressure brings relief,\n" +
                "Bryonia quells the stitch of grief.");

        put("cact", "Cactus grandiflorus",
                "Iron band around the chest,\n" +
                "Gripping heart without a rest;\n" +
                "Suffocative constriction tight,\n" +
                "Cactus turns the day from night.");

        put("calc", "Calcarea carbonica",
                "Cold damp feet and sweaty head,\n" +
                "Craves for eggs, in softness bred;\n" +
                "Slow in growth, the bones delayed,\n" +
                "Calcarea builds the frame arrayed.");

        put("calc-f", "Calcarea fluorica",
                "Hard stony glands and bone deposit,\n" +
                "Varicose veins and elastic deficit;\n" +
                "Cracked skin and cataracts gray,\n" +
                "Calc fluor melts the stones away.");

        put("calc-p", "Calcarea phosphorica",
                "Delayed bones that will not knit,\n" +
                "Rickets, scoliosis, growth unfit;\n" +
                "Cranial fontanelles open wide,\n" +
                "Calc phos builds the bone inside.");

        put("calc-s", "Calcarea sulphurica",
                "Thick lumpy yellow pus with blood,\n" +
                "Cystic acne in a flood;\n" +
                "Suppuration having found a vent,\n" +
                "Calcarea sulph is heaven sent.");

        put("calen", "Calendula officinalis",
                "Lacerated wounds and bleeding raw,\n" +
                "Heals the cut and broken jaw;\n" +
                "No suppuration, scars depart,\n" +
                "Calendula brings the surgeon's art.");

        put("camph", "Camphora",
                "Cold as ice, yet strips the sheet,\n" +
                "Sudden collapse and vanishing beat;\n" +
                "Rice-water flux and cramps that kill,\n" +
                "Camphora halts the dying chill.");

        put("cann-i", "Cannabis indica",
                "Seconds seem like centuries long,\n" +
                "Laughs at nothing, bursts in song;\n" +
                "Clairvoyant space expands on high,\n" +
                "Cannabis paints the astral sky.");

        put("canth", "Cantharis vesicatoria",
                "Constant urging, drop by drop,\n" +
                "Scalding urine will not stop;\n" +
                "Burning knives in bladder deep,\n" +
                "Cantharis brings the sufferer sleep.");

        put("carb-v", "Carbo vegetabilis",
                "Faint and blue with icy breath,\n" +
                "Lying in the shade of death;\n" +
                "'Fan me! Fan me!' is the cry,\n" +
                "Carbo veg will never die.");

        put("caul", "Caulophyllum thalictroides",
                "Erratic labor, pains that fly,\n" +
                "Rigid os and weeping sigh;\n" +
                "Small joint pains of hand and feet,\n" +
                "Caulophyllum makes delivery sweet.");

        put("caust", "Causticum",
                "Involuntary urine when she coughs,\n" +
                "Tears for others, sorrow doffs;\n" +
                "Paralysis of local part,\n" +
                "Causticum warms the tender heart.");

        put("cham", "Chamomilla",
                "Snappy, cross, and full of spite,\n" +
                "One cheek pale, the other bright;\n" +
                "Pains unbearable, wants to roam,\n" +
                "Chamomilla brings peace home.");

        put("chel", "Chelidonium majus",
                "Right scapula angle, aching deep,\n" +
                "Yellow eyes and bilious sleep;\n" +
                "Craves hot drinks and liver clear,\n" +
                "Chelidonium banishes fear.");

        put("chin", "Cinchona officinalis",
                "Drained of blood and vital juice,\n" +
                "Hollow temples, ringing truce;\n" +
                "Bloated belly, bitter gall,\n" +
                "China answers when they fall.");

        put("cic", "Cicuta virosa",
                "Tetanic spasms, head thrown back,\n" +
                "Violent convulsive epileptic track;\n" +
                "Childish mind and chewing jaw,\n" +
                "Cicuta calms the spastic flaw.");

        put("cina", "Cina maritima",
                "Picks the nose and grinds the teeth,\n" +
                "Worms that burrow underneath;\n" +
                "Cross and ugly, screams with dread,\n" +
                "Cina puts the child to bed.");

        put("cocc", "Cocculus indicus",
                "Nausea from the motion round,\n" +
                "Watching sick without a bound;\n" +
                "Vertigo and empty head,\n" +
                "Cocculus lifts him from the bed.");

        put("coff", "Coffea cruda",
                "Mind excited, thoughts run fast,\n" +
                "Cannot sleep while senses last;\n" +
                "Pain is torture, joy too deep,\n" +
                "Coffea brings the quiet sleep.");

        put("colch", "Colchicum autumnale",
                "Smell of cooking makes him retch,\n" +
                "Big toe swollen in the stretch;\n" +
                "Gouty joints and urine red,\n" +
                "Colchicum eases the aching bed.");

        put("coloc", "Colocynthis",
                "Agonizing colic, bending double,\n" +
                "Pressing hard relieves the trouble;\n" +
                "Anger brought the cutting pain,\n" +
                "Colocynth makes comfort reign.");

        put("con", "Conium maculatum",
                "Glands like stones and vertigo,\n" +
                "Turning head in bed brings woe;\n" +
                "Ascending weakness, cancer fears,\n" +
                "Conium wipes the elder's tears.");

        put("cupr", "Cuprum metallicum",
                "Violent cramps in calf and toe,\n" +
                "Thumbs clenched tight in spastic woe;\n" +
                "Spasmodic cough with face turning blue,\n" +
                "Cuprum brings the rhythm true.");

        put("dig", "Digitalis purpurea",
                "Slow weak pulse that misses beats,\n" +
                "Fears the heart will stop its seats;\n" +
                "Sits upright to catch his breath,\n" +
                "Digitalis conquers death.");

        put("dros", "Drosera rotundifolia",
                "Barking cough, paroxysmal roar,\n" +
                "Whooping spasms by the score;\n" +
                "Holds the chest and vomits food,\n" +
                "Drosera heals the bronchial brood.");

        put("dulc", "Dulcamara",
                "Caught in cold and rainy damp,\n" +
                "Joints that swell and bowels that cramp;\n" +
                "Warts on hands and catarrhal rheum,\n" +
                "Dulcamara clears the gloom.");

        put("echi", "Echinacea angustifolia",
                "Blood poisoned, foul and septic drain,\n" +
                "Carbuncle of stinging pain;\n" +
                "Snake-bite, gangrene, fever slow,\n" +
                "Echinacea strikes the blow.");

        put("eup-per", "Eupatorium perfoliatum",
                "Aching bones as if they'd break,\n" +
                "Influenza's bitter ache;\n" +
                "Thirst before the fever chill,\n" +
                "Boneset conquers dengue's ill.");

        put("euphr", "Euphrasia officinalis",
                "Acrid tears that scald the cheek,\n" +
                "Bland discharge from nostrils meek;\n" +
                "Photophobia, eyes so sore,\n" +
                "Euphrasia heals the sight once more.");

        put("ferr", "Ferrum metallicum",
                "Flushed red face upon the least,\n" +
                "Paleness masked in pseudo-feast;\n" +
                "Undigested food returned,\n" +
                "Ferrum met has anemia learned.");

        put("ferr-p", "Ferrum phosphoricum",
                "Early stage of fever mild,\n" +
                "Congested chest in growing child;\n" +
                "Soft quick pulse and cheeks aglow,\n" +
                "Ferrum phos makes healing flow.");

        put("gels", "Gelsemium sempervirens",
                "Dull and drowsy, dizzy gaze,\n" +
                "Trembling limbs in nervous daze;\n" +
                "Thirstless with a heavy head,\n" +
                "Gelsemium lifts the soul from bed.");

        put("glon", "Glonoinum",
                "Pounding carotids, bursting brain,\n" +
                "Cannot bear the heat or train;\n" +
                "Sunstroke terror, lost his street,\n" +
                "Glonoin stops the pounding heat.");

        put("graph", "Graphites",
                "Thick sticky honey from the skin,\n" +
                "Cracked behind the ear and chin;\n" +
                "Chilly, stout, and bowels delayed,\n" +
                "Graphites heals the skin displayed.");

        put("ham", "Hamamelis virginica",
                "Passive, dark and venous flow,\n" +
                "Bruised soreness down below;\n" +
                "Varicose veins and bleeding piles,\n" +
                "Hamamelis brings the smiles.");

        put("hell", "Helleborus niger",
                "Senses stuporous, sees no light,\n" +
                "Automatic motion night;\n" +
                "Hydrocephalus dropsy cold,\n" +
                "Hellebore breaks the stupor's hold.");

        put("hepar", "Hepar sulphuris calcareum",
                "Chilly, sensitive to the breeze,\n" +
                "Splinter in throat that makes him wheeze;\n" +
                "Violent temper, suppurations near,\n" +
                "Hepar sulph brings healing clear.");
        put("hep", "Hepar sulphuris calcareum",
                "Chilly, sensitive to the breeze,\n" +
                "Splinter in throat that makes him wheeze;\n" +
                "Violent temper, suppurations near,\n" +
                "Hepar sulph brings healing clear.");

        put("hydr", "Hydrastis canadensis",
                "Thick ropy yellow mucus string,\n" +
                "Goneness at stomach like a sting;\n" +
                "Slow digestion, ulcers gray,\n" +
                "Hydrastis clears the sludge away.");

        put("hyos", "Hyoscyamus niger",
                "Shameless nudity, strips his gown,\n" +
                "Jealous mutterings around;\n" +
                "Dry night cough relieved sitting up,\n" +
                "Hyoscyamus calms the frantic cup.");

        put("hyper", "Hypericum perforatum",
                "Crushed nerve endings, toes and spine,\n" +
                "Shooting pains up in a line;\n" +
                "Puncture wounds by needle or nail,\n" +
                "Hypericum will never fail.");

        put("ign", "Ignatia amara",
                "Silent grief and frequent sigh,\n" +
                "Paradoxical laugh and cry;\n" +
                "Lump in throat that swallows food,\n" +
                "Ignatia heals the sorrowed mood.");

        put("iod", "Iodium",
                "Ravenous hunger, eats all day,\n" +
                "Loses flesh and wastes away;\n" +
                "Glands indurated, hot and bold,\n" +
                "Iodine quenches metabolism cold.");

        put("ip", "Ipecacuanha",
                "Persistent nausea, tongue so clean,\n" +
                "Vomiting leaves no relief between;\n" +
                "Spasmodic cough and hemorrhages bright,\n" +
                "Ipecac sets the stomach right.");

        put("kali-bi", "Kali bichromicum",
                "Ropy strings of yellow slime,\n" +
                "Punched-out ulcers formed with time;\n" +
                "Pain in spots beneath the finger,\n" +
                "Kali bich lets nothing linger.");

        put("kali-c", "Kali carbonicum",
                "Stitching chest pains, three to four,\n" +
                "Swelling lids and backache sore;\n" +
                "Weakened heart and chilly breath,\n" +
                "Kali carb stays impending death.");

        put("kali-m", "Kali muriaticum",
                "Second stage with exudate white,\n" +
                "Thick white tongue and deafness tight;\n" +
                "Glandular swelling, fibrinous clot,\n" +
                "Kali mur heals the catarrhal spot.");

        put("kali-p", "Kali phosphoricum",
                "Brain-fag, nervous breakdown deep,\n" +
                "Tired to death, no rest in sleep;\n" +
                "Golden salt for brain and nerve,\n" +
                "Kali phos restores the curve.");

        put("kali-s", "Kali sulphuricum",
                "Yellow slimy mucus peeling,\n" +
                "Third stage of catarrhal healing;\n" +
                "Shifting pains in joints that cry,\n" +
                "Kali sulph brings comfort nigh.");

        put("kalm", "Kalmia latifolia",
                "Shooting pains that downward trend,\n" +
                "Heart and joints together bend;\n" +
                "Pulse is slow and numbness wide,\n" +
                "Kalmia turns the rheum aside.");

        put("kreos", "Kreosotum",
                "Teeth decay as soon as seen,\n" +
                "Excoriating flux between;\n" +
                "Burning discharges foul and dark,\n" +
                "Kreosote shields the vital spark.");

        put("lac-c", "Lac caninum",
                "Erratic pains that change their side,\n" +
                "Left to right, then back they ride;\n" +
                "Tonsils swollen, mastitis keen,\n" +
                "Lac caninum clears the screen.");

        put("lach", "Lachesis muta",
                "Left to right, sleeps into pain,\n" +
                "Tight collars choke upon the vein;\n" +
                "Loquacious speech and jealous dart,\n" +
                "Lachesis frees the suffocating heart.");

        put("led", "Ledum palustre",
                "Punctured wounds and wounded sole,\n" +
                "Ascending rheumatism takes its toll;\n" +
                "Cold as ice, but hates the heat,\n" +
                "Ledum soothes the aching feet.");

        put("lyc", "Lycopodium clavatum",
                "Right to left, from four to eight,\n" +
                "Flatus swells in bloated state;\n" +
                "Craves warm food and drinks so hot,\n" +
                "Lycopodium clears the liver spot.");

        put("mag-c", "Magnesia carbonica",
                "Green frothy stool like pond-scum scum,\n" +
                "Tearing facial neuralgias come;\n" +
                "Sour smelling, colicky moan,\n" +
                "Mag carb calms the baby's groan.");

        put("mag-p", "Magnesia phosphorica",
                "Excruciating cramps that dart and bite,\n" +
                "Heat and pressure put them right;\n" +
                "Neuralgia sharp as lightning flash,\n" +
                "Mag phos halts the painful clash.");

        put("med", "Medorrhinum",
                "Hurried, anxious, time runs slow,\n" +
                "Knee-chest sleeping brings the glow;\n" +
                "Sycotic catarrh, joints in pain,\n" +
                "Medorrhinum clears the toxic strain.");

        put("merc", "Mercurius solubilis",
                "Sweat that gives no cool relief,\n" +
                "Flabby tongue with teeth-mark grief;\n" +
                "Creeping chills and nocturnal pain,\n" +
                "Mercurius cleanses the toxic strain.");

        put("merc-c", "Mercurius corrosivus",
                "Tenesmus in the bowel and seat,\n" +
                "Blood and shreds in burning heat;\n" +
                "Every drop a cutting knife,\n" +
                "Merc cor restores the dying life.");

        put("mez", "Mezereum",
                "Thick white crusts on head and face,\n" +
                "Pus collects beneath their place;\n" +
                "Violent periosteal shinbone pain,\n" +
                "Mezereum makes them whole again.");

        put("nat-c", "Natrum carbonicum",
                "Headache from the sun and heat,\n" +
                "Ankles give way beneath the feet;\n" +
                "Dyspepsia caused by drinking milk,\n" +
                "Natrum carb soothes the lining silk.");

        put("nat-m", "Natrum muriaticum",
                "Craves for salt and weeps alone,\n" +
                "Consolation turns to stone;\n" +
                "Sunlight headache, mapped tongue dry,\n" +
                "Natrum mur clears the teardrop eye.");

        put("nat-p", "Natrum phosphoricum",
                "Golden-yellow creamy tongue,\n" +
                "Acid eructations flung;\n" +
                "Sour vomiting, colic tight,\n" +
                "Natrum phos sets pH right.");

        put("nat-s", "Natrum sulphuricum",
                "Damp wet weather brings the wheeze,\n" +
                "Sycotic liver, bilious breeze;\n" +
                "Loose morning stool and head-fall pain,\n" +
                "Natrum sulph clears the sodden rain.");

        put("nit-ac", "Nitricum acidum",
                "Splinters sticking in the flesh,\n" +
                "Ulcers ragged, raw and fresh;\n" +
                "Urine smells like horse's stall,\n" +
                "Nitric acid heals it all.");

        put("nux-m", "Nux moschata",
                "Overwhelming sleepy daze,\n" +
                "Dry mouth without thirst always;\n" +
                "Tongue sticks to the roof inside,\n" +
                "Nux moschata stems the tide.");

        put("nux-v", "Nux vomica",
                "Irritable, chilly, works all night,\n" +
                "Spicy food and wine in sight;\n" +
                "Ineffectual urge to stool,\n" +
                "Nux vomica restores the digestive rule.");

        put("op", "Opium",
                "Painless stupor, snoring breath,\n" +
                "Face dark red, as near to death;\n" +
                "Hot sweat flows and bowels stand still,\n" +
                "Opium conquers the frightful chill.");

        put("petr", "Petroleum",
                "Cracked fingertips in winter cold,\n" +
                "Bleeding deep in skin unfold;\n" +
                "Motion sickness, riding train,\n" +
                "Petroleum clears the dizzy brain.");

        put("ph-ac", "Phosphoricum acidum",
                "Grief and sorrow, memory flat,\n" +
                "Apathetic where he sat;\n" +
                "Milky urine, vital waste,\n" +
                "Phosphoric acid heals in haste.");

        put("phos", "Phosphorus",
                "Tall and slender, craves for cold,\n" +
                "Burning spine and bleeding bold;\n" +
                "Fear of thunderstorms and the dark,\n" +
                "Phosphorus rekindles the vital spark.");

        put("phyt", "Phytolacca decandra",
                "Breasts like stone when baby feeds,\n" +
                "Pains shoot round like flying reeds;\n" +
                "Dark red throat and aching bone,\n" +
                "Phytolacca cures the mother's moan.");

        put("plat", "Platinum metallicum",
                "Pride and haughtiness on her brow,\n" +
                "All look small who greet her now;\n" +
                "Numbness, cramps and genital fire,\n" +
                "Platina calms the proud desire.");

        put("plb", "Plumbum metallicum",
                "Lead colic pulled into the spine,\n" +
                "Wrist-drop palsy on the line;\n" +
                "Blue line on the gums appears,\n" +
                "Plumbum calms the motor fears.");

        put("podo", "Podophyllum peltatum",
                "Early morning pouring gush,\n" +
                "Hydrant stool in hurried rush;\n" +
                "Painless, fetid, draining dry,\n" +
                "Podophyllum hears the baby cry.");

        put("psor", "Psorinum",
                "Wears a coat in summer heat,\n" +
                "Rotten smell from head to feet;\n" +
                "Hopeless, itchy, hopeless mind,\n" +
                "Psorinum leaves the dirt behind.");

        put("puls", "Pulsatilla nigricans",
                "Gentle, mild, and weeping sweet,\n" +
                "Wandering pains and chilly heat;\n" +
                "Thirstless with an open-air desire,\n" +
                "Pulsatilla quenches the changeable fire.");

        put("pyrog", "Pyrogenium",
                "Pulse too fast for fever low,\n" +
                "Or fever high with pulse too slow;\n" +
                "Bed feels hard and limbs in ache,\n" +
                "Pyrogen bids septic storms break.");

        put("rhus-t", "Rhus toxicodendron",
                "Restless limbs in stormy rain,\n" +
                "First motion hurts with aching pain;\n" +
                "Better by continuous motion and heat,\n" +
                "Rhus tox puts springs upon the feet.");

        put("ruta", "Ruta graveolens",
                "Bruised bones and tendons tight,\n" +
                "Eyestrain sewing in the night;\n" +
                "Ganglion wrists and periosteum sore,\n" +
                "Ruta graveolens heals the core.");

        put("sabad", "Sabadilla",
                "Spasmodic sneezing, nose on fire,\n" +
                "Imagines sickness in her desire;\n" +
                "Worm complaints and throat feels tight,\n" +
                "Sabadilla puts the sneeze to flight.");

        put("sabin", "Sabina",
                "Sacrum to pubes, shooting straight,\n" +
                "Bright red flooding, heavy weight;\n" +
                "Gouty joints and abortion fear,\n" +
                "Sabina brings the remedy near.");

        put("sang", "Sanguinaria canadensis",
                "Right-sided headache with the sun,\n" +
                "Occiput to eye does run;\n" +
                "Climacteric flush and burning palm,\n" +
                "Sanguinaria restores the calm.");

        put("sec", "Secale cornutum",
                "Cold as ice, but hates the sheet,\n" +
                "Dry black gangrene in the feet;\n" +
                "Continuous hemorrhage, burning glow,\n" +
                "Secale halts the necrotic flow.");

        put("sep", "Sepia officinalis",
                "Bearing down, must cross the knees,\n" +
                "Indifferent to those she used to please;\n" +
                "Yellow saddle across the nose,\n" +
                "Sepia restores the woman's repose.");

        put("sil", "Silicea terra",
                "Lacks the grit and dreads the draft,\n" +
                "Cold damp feet that smell with craft;\n" +
                "Foreign bodies, splinters expelled with might,\n" +
                "Silicea builds the spine upright.");

        put("spig", "Spigelia anthelmia",
                "Left eye neuralgia, violent pace,\n" +
                "Rises and falls with the sun's embrace;\n" +
                "Stitching heart with visible beat,\n" +
                "Spigelia stays the throbbing heat.");

        put("spong", "Spongia tosta",
                "Barking croup like saw on wood,\n" +
                "Suffocating where he stood;\n" +
                "Worse before midnight, better warm drink,\n" +
                "Spongia draws back from the brink.");

        put("stann", "Stannum metallicum",
                "Chest so weak he cannot speak,\n" +
                "Sweetish sputum day and week;\n" +
                "Emptiness within the breast,\n" +
                "Stannum brings the lungs to rest.");

        put("staph", "Staphysagria",
                "Suppressed anger, bitter humiliation,\n" +
                "Honeymoon cystitis, sharp urination;\n" +
                "Clean-cut surgical wounds that tear,\n" +
                "Staphysagria answers the prayer.");

        put("stram", "Stramonium",
                "Terrified in dark of night,\n" +
                "Water and shadows cause him fright;\n" +
                "Wild delirium, stammering speech,\n" +
                "Stramonium brings peace within reach.");

        put("sulph", "Sulphur",
                "Ragged philosopher, heat at crown,\n" +
                "Soles burn hot, covers kicked down;\n" +
                "Eleven AM hunger in the pit of chest,\n" +
                "Sulphur puts chronic storms to rest.");

        put("symph", "Symphytum officinale",
                "Broken bones that will not mend,\n" +
                "Blunt eye blows from foe or friend;\n" +
                "Callus forms upon the fracture line,\n" +
                "Symphytum makes the bone align.");

        put("syph", "Syphilinum",
                "Bone pains worse from night till dawn,\n" +
                "Linear ulcers, hope is gone;\n" +
                "Compulsive washing of the hand,\n" +
                "Syphilinum frees the burdened land.");

        put("tab", "Tabacum",
                "Icy coldness, nausea profound,\n" +
                "Seasickness spinning round and round;\n" +
                "Cold sweat poured, uncovers chest,\n" +
                "Tabacum sets the sea at rest.");

        put("thuj", "Thuja occidentalis",
                "Wart and condyloma grow,\n" +
                "Vaccine poison creeping slow;\n" +
                "Left side worse, body feels like glass,\n" +
                "Thuja bids sycotic shadows pass.");

        put("tub", "Tuberculinum bovinum",
                "Constantly changing, wants to roam,\n" +
                "Never content to stay at home;\n" +
                "Catching cold from the slightest breeze,\n" +
                "Tuberculinum cures the chest disease.");

        put("verat", "Veratrum album",
                "Icy cold sweat upon the brow,\n" +
                "Violent retching vomiting now;\n" +
                "Rice-water stools with fainting chill,\n" +
                "Veratrum conquers cholera's ill.");

        put("zinc", "Zincum metallicum",
                "Fidgety feet that cannot rest,\n" +
                "Suppressed eruptions in the chest;\n" +
                "Brain too weak to show the rash,\n" +
                "Zincum averts the nervous crash.");

        // Indian Indigenous Remedies
        put("andro-p", "Andrographis paniculata",
                "King of bitters, liver sluggish and slow,\n" +
                "Post-malarial fever, yellow tongue aglow;\n" +
                "Clears the bile and burns the chill,\n" +
                "Kalmegh conquers the bilious ill.");

        put("azad-i", "Azadirachta indica",
                "Bitter tree of ancient fame,\n" +
                "Purifies blood and cools the flame;\n" +
                "Boils and eczema yield their strife,\n" +
                "Neem restores the skin to life.");

        put("boer-d", "Boerhaavia diffusa",
                "Dropsy swelling limb and face,\n" +
                "Sluggish kidneys lose the race;\n" +
                "Renews the body, drains the tide,\n" +
                "Punarnava walks by the kidney's side.");

        put("ceph-i", "Cephalandra indica",
                "Thirst unquenched in sugar drain,\n" +
                "Frequent urine, diabetic pain;\n" +
                "Carbuncles threatening on the skin,\n" +
                "Cephalandra cools the fire within.");

        put("cynod-d", "Cynodon dactylon",
                "Sacred grass of green repose,\n" +
                "Stops the bleeding from the nose;\n" +
                "Uterine flooding, burning heat,\n" +
                "Durva cools the hurried beat.");

        put("ficus-r", "Ficus religiosa",
                "Peepal leaf of sacred power,\n" +
                "Stops bright bleeding in an hour;\n" +
                "Lungs or bowel or kidney vein,\n" +
                "Ficus brings relief again.");

        put("gymn-s", "Gymnema sylvestre",
                "Destroys the taste of sugar sweet,\n" +
                "Takes the glucose from the sheet;\n" +
                "Carbuncle wounds and thirst depart,\n" +
                "Gurmar heals the diabetic heart.");

        put("holar-a", "Holarrhena antidysenterica",
                "Amoebic griping, mucus red,\n" +
                "Frequent stool and aching bed;\n" +
                "Cools the colon's burning fire,\n" +
                "Kurchi grants the sick desire.");

        put("hydro-a", "Hydrocotyle asiatica",
                "Thickened skin and circular scale,\n" +
                "Where ordinary lotions fail;\n" +
                "Uterine neck and granular plight,\n" +
                "Hydrocotyle sets skin upright.");

        put("jon-a", "Jonosia asoka",
                "Sacred tree for woman's sorrow,\n" +
                "Brings the hope of bright tomorrow;\n" +
                "Delayed menses, pelvic ache,\n" +
                "Ashoka bids the bloom awake.");

        put("just-a", "Justicia adhatoda",
                "Suffocative cough in bronchial chest,\n" +
                "Nocturnal choking denies all rest;\n" +
                "Blood-streaked phlegm and whooping roar,\n" +
                "Vasaka clears the breath once more.");

        put("nyct-a", "Nyctanthes arbor-tristis",
                "Night-flowering jasmine, bitter tea,\n" +
                "Sets the sciatic sufferer free;\n" +
                "Bilious fever, obstinate chill,\n" +
                "Parijat cures the stubborn ill.");

        put("ocim-s", "Ocimum sanctum",
                "Holy basil, soothing leaf,\n" +
                "Brings the fever prompt relief;\n" +
                "Cough and coryza, chest oppressed,\n" +
                "Tulsi leaves the breath at rest.");

        put("rauw-s", "Rauwolfia serpentina",
                "Bounding pulse and violent strain,\n" +
                "Throbbing pressure in the brain;\n" +
                "Soothes the nerves to quiet sleep,\n" +
                "Sarpagandha's calm runs deep.");

        put("syzyg-j", "Syzygium jambolanum",
                "Seeds of Jamun, dark and deep,\n" +
                "Halt the sugar while they sleep;\n" +
                "Pruritus burning, urine high,\n" +
                "Syzygium hears the patient's sigh.");

        put("term-a", "Terminalia arjuna",
                "Failing heart and choking breath,\n" +
                "Angina pains like impending death;\n" +
                "Strengthens the cardiac muscle wall,\n" +
                "Arjuna answers the healer's call.");

        put("tinos-c", "Tinospora cordifolia",
                "Nectar vine that never dies,\n" +
                "Immune defense begins to rise;\n" +
                "Chronic fevers, rheumatic pain,\n" +
                "Giloy makes them whole again.");

        put("with-s", "Withania somnifera",
                "Vital horse of strength and grace,\n" +
                "Nervous breakdown, weary pace;\n" +
                "Restores the sperm and calms the head,\n" +
                "Ashwagandha lifts the dead.");

        // Aliases for common notations
        putAlias("calc-c", "calc");
        putAlias("rauw", "rauw-s");
        putAlias("arjun", "term-a");
        putAlias("neem", "azad-i");
        putAlias("tulsi", "ocim-s");
        putAlias("kalmegh", "andro-p");
        putAlias("giloy", "tinos-c");
        putAlias("ashwagandha", "with-s");
        putAlias("kurchi", "holar-a");
        putAlias("brahmi", "hydro-a");
        putAlias("vasaka", "just-a");
        putAlias("ashoka", "saraca-i");
        putAlias("jamun", "syzyg-j");
        putAlias("punarnava", "boer-d");
        putAlias("gurmar", "gymn-s");
    }

    private static void putAlias(String alias, String targetKey) {
        String rhyme = RHYMES.get(targetKey.toLowerCase());
        if (rhyme != null) {
            RHYMES.put(alias.toLowerCase(), rhyme);
        }
    }

    private static void put(String id, String latinName, String rhyme) {
        RHYMES.put(id.toLowerCase(), rhyme);
        RHYMES.put(latinName.toLowerCase(), rhyme);
        String firstWord = latinName.split(" ")[0].toLowerCase();
        if (!RHYMES.containsKey(firstWord)) {
            RHYMES.put(firstWord, rhyme);
        }
    }

    /**
     * Looks up the clinical rhyme for a remedy by its ID, abbreviation, or Latin name.
     * Returns null if no isolated verse is registered.
     */
    public static String getRhyme(String remedyIdOrName) {
        if (remedyIdOrName == null || remedyIdOrName.isBlank()) return null;
        String key = remedyIdOrName.trim().toLowerCase();
        if (key.endsWith(".")) key = key.substring(0, key.length() - 1);
        if (RHYMES.containsKey(key)) {
            return RHYMES.get(key);
        }
        String firstWord = key.split(" ")[0];
        if (RHYMES.containsKey(firstWord)) {
            return RHYMES.get(firstWord);
        }
        return null;
    }

    /**
     * Intelligently resolves a clinical rhyme from remedy ID, Latin binomial, or Common/Indian name.
     */
    public static String findRhyme(String remedyId, String latinName, String commonName) {
        String rhyme = getRhyme(remedyId);
        if (rhyme != null) return rhyme;
        if (latinName != null && !latinName.isBlank()) {
            rhyme = getRhyme(latinName);
            if (rhyme != null) return rhyme;
        }
        if (commonName != null && !commonName.isBlank()) {
            for (String part : commonName.split("[/,]")) {
                rhyme = getRhyme(part.trim());
                if (rhyme != null) return rhyme;
            }
        }
        return null;
    }

    public static Map<String, String> getAllRhymes() {
        return Collections.unmodifiableMap(RHYMES);
    }
}
