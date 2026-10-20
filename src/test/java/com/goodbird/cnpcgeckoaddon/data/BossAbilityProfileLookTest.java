package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The look half of an ability's profile: the warning's colour, style, width and lead, the
 * announcement and its sound, the name and the two animations - what each saves as, what an
 * older profile reads back as, how a typed colour is read, and the ranges a tag may put into
 * the numbers.
 */
class BossAbilityProfileLookTest {

    private static final String[] NUMBER_KEYS = {"TelegraphColor", "TelegraphWidth", "TelegraphLead",
            "Announce", "AnnounceSoundMode"};

    @Test
    @DisplayName("a fresh profile looks the way the boss always did")
    void defaultsAreTheOldLook() {
        BossAbilityProfile profile = new BossAbilityProfile();
        assertEquals(BossAbilityProfile.COLOR_OF_KIND, profile.getTelegraphColor());
        assertEquals(BossAbilityProfile.STYLE_AS_BOSS, profile.getTelegraphStyle());
        assertEquals(BossAbilityProfile.AS_BOSS, profile.getTelegraphLineWidth());
        assertEquals(BossAbilityProfile.AS_BOSS, profile.getTelegraphLeadTicks());
        assertEquals(BossAbilityProfile.ANNOUNCE_AS_BOSS, profile.getAnnounceMode());
        assertEquals(BossAbilityProfile.ANNOUNCE_SOUND_AS_BOSS, profile.getAnnounceSoundMode());
        assertEquals("minecraft:block.note_block.bell", profile.getAnnounceSound().getSoundId());
        assertEquals(8, profile.getAnnounceSound().getVolume());
        assertEquals(6, profile.getAnnounceSound().getPitch());
        assertTrue(profile.getAnnounceSound().isEnabled(), "the own sound plays once the mode asks for it");
        assertEquals("", profile.getDisplayName());
        assertEquals("", profile.getCastAnimation());
        assertEquals("", profile.getEndAnimation());
        assertTrue(profile.isLookDefault());
        assertTrue(profile.isDefault());

        // And the helpers hand the boss' own back for every field left alone.
        assertEquals(0x123456, profile.colorOr(0x123456));
        assertEquals(TelegraphLineStyles.DASHED, profile.styleOr(TelegraphLineStyles.DASHED));
        assertEquals(7, profile.lineWidthOr(7));
        assertEquals(30, profile.leadOr(30));
        assertTrue(profile.announces(true));
        assertFalse(profile.announces(false));
    }

    @Test
    @DisplayName("strings, colour and cue round-trip through the phase under their own keys")
    void theFieldsRoundTrip() {
        BossPhaseData phase = new BossPhaseData();
        BossAbilityProfile area = phase.profile(BossAbilityKind.AREA);
        area.setTelegraphColor(0xFF2020);
        area.setTelegraphStyle(TelegraphLineStyles.GLOW);
        area.setTelegraphLineWidth(12);
        area.setTelegraphLeadTicks(60);
        area.setAnnounceMode(BossAbilityProfile.ANNOUNCE_OFF);
        area.setAnnounceSoundMode(BossAbilityProfile.ANNOUNCE_SOUND_OWN);
        area.getAnnounceSound().setSoundId("minecraft:block.note_block.pling");
        area.getAnnounceSound().setVolume(15);
        area.getAnnounceSound().setPitch(20);
        area.setDisplayName("  Инферно  ");
        area.setCastAnimation(" slam_cast ");
        area.setEndAnimation("slam_end");

        CompoundTag tag = phase.writeToNBT();
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(tag);
        BossAbilityProfile back = reread.profile(BossAbilityKind.AREA);
        assertEquals(0xFF2020, back.getTelegraphColor());
        assertEquals(TelegraphLineStyles.GLOW, back.getTelegraphStyle());
        assertEquals(12, back.getTelegraphLineWidth());
        assertEquals(60, back.getTelegraphLeadTicks());
        assertEquals(BossAbilityProfile.ANNOUNCE_OFF, back.getAnnounceMode());
        assertEquals(BossAbilityProfile.ANNOUNCE_SOUND_OWN, back.getAnnounceSoundMode());
        assertEquals("minecraft:block.note_block.pling", back.getAnnounceSound().getSoundId());
        assertEquals(15, back.getAnnounceSound().getVolume());
        assertEquals(20, back.getAnnounceSound().getPitch());
        assertEquals("Инферно", back.getDisplayName(), "a name is trimmed");
        assertEquals("slam_cast", back.getCastAnimation(), "an animation is trimmed");
        assertEquals("slam_end", back.getEndAnimation());
        assertTrue(reread.profile(BossAbilityKind.LINE).isDefault(), "an untouched ability reads back untouched");
        assertEquals(tag, reread.writeToNBT(), "a re-read phase saves the same tag again");

        CompoundTag written = new CompoundTag();
        area.writeToNBT(written);
        for (String key : new String[]{"TelegraphColor", "TelegraphStyle", "TelegraphWidth", "TelegraphLead",
                "Announce", "AnnounceSoundMode", "AnnounceSoundOn", "AnnounceSoundSound", "AnnounceSoundVolume",
                "AnnounceSoundPitch", "Name", "CastAnimation", "EndAnimation"}) {
            assertTrue(written.contains(key), "the profile does not write " + key);
        }
    }

    @Test
    @DisplayName("a profile saved before the look reads it back as the boss' own")
    void anOlderProfileReadsTheDefaults() {
        CompoundTag older = new CompoundTag();
        older.putInt("Priority", 4);
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setTelegraphColor(0x00FF00);
        profile.setTelegraphStyle(TelegraphLineStyles.SOLID);
        profile.setTelegraphLineWidth(5);
        profile.setTelegraphLeadTicks(10);
        profile.setAnnounceMode(BossAbilityProfile.ANNOUNCE_ON);
        profile.setAnnounceSoundMode(BossAbilityProfile.ANNOUNCE_SOUND_OFF);
        profile.getAnnounceSound().setEnabled(false);
        profile.setDisplayName("Old");
        profile.setCastAnimation("a");
        profile.setEndAnimation("b");
        profile.readFromNBT(older);
        assertEquals(4, profile.getPriority());
        assertTrue(profile.isLookDefault(), "an absent key has to read as its default, not keep what was there: "
                + profile);
    }

    @Test
    @DisplayName("a colour is six hex digits, with or without a hash; nothing is the kind's colour")
    void coloursAreParsed() {
        assertEquals(0xFF2020, BossAbilityProfile.parseColor("FF2020"));
        assertEquals(0xFF2020, BossAbilityProfile.parseColor("#FF2020"));
        assertEquals(0xAB12CD, BossAbilityProfile.parseColor(" #ab12cd "), "either case, spaces around");
        assertEquals(0x000000, BossAbilityProfile.parseColor("000000"), "black is a colour");
        assertEquals(BossAbilityProfile.COLOR_OF_KIND, BossAbilityProfile.parseColor(""));
        assertEquals(BossAbilityProfile.COLOR_OF_KIND, BossAbilityProfile.parseColor("   "));
        assertEquals(BossAbilityProfile.COLOR_OF_KIND, BossAbilityProfile.parseColor("#"));
        assertEquals(BossAbilityProfile.COLOR_OF_KIND, BossAbilityProfile.parseColor(null));
        for (String junk : new String[]{"F00", "FF20201", "GG2020", "red", "-FFFFF", "+FFFFF", "0xFF20",
                "##FF2020", "FF 020"}) {
            assertEquals(BossAbilityProfile.COLOR_INVALID, BossAbilityProfile.parseColor(junk),
                    "'" + junk + "' is no colour");
        }

        assertEquals("FF2020", BossAbilityProfile.formatColor(0xFF2020));
        assertEquals("00000A", BossAbilityProfile.formatColor(10), "always six digits");
        assertEquals("", BossAbilityProfile.formatColor(BossAbilityProfile.COLOR_OF_KIND));
        assertEquals(0x00000A, BossAbilityProfile.parseColor(BossAbilityProfile.formatColor(10)),
                "what the field shows reads back as the same colour");
    }

    @Test
    @DisplayName("every number a tag holds is clamped, the lead and the width with the boss' own at the bottom")
    void theNumbersAreClamped() {
        CompoundTag high = new CompoundTag();
        CompoundTag low = new CompoundTag();
        for (String key : NUMBER_KEYS) {
            high.putInt(key, Integer.MAX_VALUE);
            low.putInt(key, Integer.MIN_VALUE);
        }
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.readFromNBT(high);
        assertEquals(BossAbilityProfile.MAX_COLOR, profile.getTelegraphColor());
        assertEquals(TeleportPathData.MAX_TELEGRAPH_LINE_WIDTH, profile.getTelegraphLineWidth());
        assertEquals(TeleportPathData.MAX_TELEGRAPH_LEAD_TICKS, profile.getTelegraphLeadTicks());
        assertEquals(BossAbilityProfile.ANNOUNCE_ON, profile.getAnnounceMode());
        assertEquals(BossAbilityProfile.ANNOUNCE_SOUND_OWN, profile.getAnnounceSoundMode());

        profile.readFromNBT(low);
        assertEquals(BossAbilityProfile.COLOR_OF_KIND, profile.getTelegraphColor());
        assertEquals(BossAbilityProfile.AS_BOSS, profile.getTelegraphLineWidth());
        assertEquals(BossAbilityProfile.AS_BOSS, profile.getTelegraphLeadTicks());
        assertEquals(BossAbilityProfile.ANNOUNCE_AS_BOSS, profile.getAnnounceMode());
        assertEquals(BossAbilityProfile.ANNOUNCE_SOUND_AS_BOSS, profile.getAnnounceSoundMode());

        // A width under the boss' narrowest is no width of its own; nought is not a band.
        CompoundTag zeroWidth = new CompoundTag();
        zeroWidth.putInt("TelegraphWidth", 0);
        profile.readFromNBT(zeroWidth);
        assertEquals(BossAbilityProfile.AS_BOSS, profile.getTelegraphLineWidth());

        // The setters the screen calls clamp the same way.
        profile.setTelegraphLeadTicks(500);
        assertEquals(BossAbilityProfile.MAX_LEAD, profile.getTelegraphLeadTicks());
        profile.setTelegraphLeadTicks(-9);
        assertEquals(BossAbilityProfile.AS_BOSS, profile.getTelegraphLeadTicks());
        profile.setTelegraphLeadTicks(0);
        assertEquals(0, profile.getTelegraphLeadTicks(), "no lead at all is a lead of its own");
        assertEquals(0, profile.leadOr(30));
        profile.setTelegraphLineWidth(0);
        assertEquals(BossAbilityProfile.AS_BOSS, profile.getTelegraphLineWidth());
        profile.setTelegraphLineWidth(99);
        assertEquals(TeleportPathData.MAX_TELEGRAPH_LINE_WIDTH, profile.getTelegraphLineWidth());
        profile.setTelegraphLineWidth(TeleportPathData.MIN_TELEGRAPH_LINE_WIDTH);
        assertEquals(TeleportPathData.MIN_TELEGRAPH_LINE_WIDTH, profile.getTelegraphLineWidth());
        profile.setTelegraphColor(-5);
        assertEquals(BossAbilityProfile.COLOR_OF_KIND, profile.getTelegraphColor());
        profile.setTelegraphColor(0x1FFFFFF);
        assertEquals(BossAbilityProfile.MAX_COLOR, profile.getTelegraphColor());
        profile.setAnnounceMode(4);
        assertEquals(BossAbilityProfile.ANNOUNCE_ON, profile.getAnnounceMode());
        profile.setAnnounceSoundMode(-4);
        assertEquals(BossAbilityProfile.ANNOUNCE_SOUND_AS_BOSS, profile.getAnnounceSoundMode());
    }

    @Test
    @DisplayName("a style the build does not know is the boss' own, not the particles")
    void stylesAreNormalized() {
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setTelegraphStyle(TelegraphLineStyles.PARTICLES);
        assertEquals(TelegraphLineStyles.PARTICLES, profile.getTelegraphStyle(), "particles is a choice of its own");
        assertEquals(TelegraphLineStyles.PARTICLES, profile.styleOr(TelegraphLineStyles.SOLID));
        profile.setTelegraphStyle("somemod:laser");
        assertEquals(BossAbilityProfile.STYLE_AS_BOSS, profile.getTelegraphStyle());
        profile.setTelegraphStyle(null);
        assertEquals(BossAbilityProfile.STYLE_AS_BOSS, profile.getTelegraphStyle());

        CompoundTag tag = new CompoundTag();
        tag.putString("TelegraphStyle", "nonsense");
        profile.readFromNBT(tag);
        assertEquals(BossAbilityProfile.STYLE_AS_BOSS, profile.getTelegraphStyle());
    }

    @Test
    @DisplayName("a name is cut to thirty-two characters, never through the middle of one")
    void namesAreCut() {
        BossAbilityProfile profile = new BossAbilityProfile();
        String forty = "Инферно".repeat(6);
        profile.setDisplayName(forty);
        assertEquals(BossAbilityProfile.MAX_NAME_LENGTH, profile.getDisplayName().length());
        assertTrue(forty.startsWith(profile.getDisplayName()));

        // Two chars per character past the basic plane: a cut by chars would split the last one.
        String faces = "🔥".repeat(40);
        profile.setDisplayName(faces);
        String kept = profile.getDisplayName();
        assertEquals(BossAbilityProfile.MAX_NAME_LENGTH, kept.codePointCount(0, kept.length()));
        assertFalse(Character.isHighSurrogate(kept.charAt(kept.length() - 1)), "the last character is whole");

        CompoundTag tag = new CompoundTag();
        tag.putString("Name", forty);
        profile.readFromNBT(tag);
        assertEquals(BossAbilityProfile.MAX_NAME_LENGTH, profile.getDisplayName().length(), "a load cuts it too");
    }

    @Test
    @DisplayName("any one look field - the cue's switch, id, volume or pitch included - makes the profile non-default")
    void eachLookFieldCounts() {
        BossAbilityProfile[] probe = new BossAbilityProfile[1];
        Runnable[] changes = {
                () -> probe[0].setTelegraphColor(0),
                () -> probe[0].setTelegraphStyle(TelegraphLineStyles.PARTICLES),
                () -> probe[0].setTelegraphLineWidth(4),
                () -> probe[0].setTelegraphLeadTicks(0),
                () -> probe[0].setAnnounceMode(BossAbilityProfile.ANNOUNCE_OFF),
                () -> probe[0].setAnnounceSoundMode(BossAbilityProfile.ANNOUNCE_SOUND_OFF),
                () -> probe[0].getAnnounceSound().setEnabled(false),
                () -> probe[0].getAnnounceSound().setSoundId("minecraft:block.note_block.pling"),
                () -> probe[0].getAnnounceSound().setVolume(1),
                () -> probe[0].getAnnounceSound().setPitch(10),
                () -> probe[0].setDisplayName("x"),
                () -> probe[0].setCastAnimation("x"),
                () -> probe[0].setEndAnimation("x")
        };
        for (int i = 0; i < changes.length; i++) {
            probe[0] = new BossAbilityProfile();
            changes[i].run();
            assertFalse(probe[0].isLookDefault(), "change " + i + " left the look default: " + probe[0]);
            assertFalse(probe[0].isDefault(), "change " + i + " left the profile default: " + probe[0]);

            // And it is written: a phase with only that change saves the profile, and reads it back.
            BossPhaseData phase = new BossPhaseData();
            phase.profile(BossAbilityKind.HUNT).copyFrom(probe[0]);
            BossPhaseData reread = new BossPhaseData();
            reread.readFromNBT(phase.writeToNBT());
            assertFalse(reread.profile(BossAbilityKind.HUNT).isDefault(), "change " + i + " was not saved");
        }
        BossAbilityProfile reset = new BossAbilityProfile();
        reset.getAnnounceSound().setVolume(1);
        reset.getAnnounceSound().reset();
        assertTrue(reset.isDefault(), "a cue put back to its call is a default cue again");
    }
}
