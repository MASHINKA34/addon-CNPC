package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The width of every per-kind mask, and what that width does to a save.
 *
 * <p>Each mask used to be an int, one bit per {@link BossAbilityKind}, so the list could not
 * grow past thirty-one kinds. Now each is a long, and nothing on disk was migrated for it: a
 * compound tag's {@code getLong} reads any numeric tag, so the int tag an older jar wrote comes
 * back as the same number, and an older jar reading the long tag this one writes gets its low
 * thirty-two bits - the whole mask, as long as no kind is numbered past thirty-one. Both halves
 * of that are pinned here, one mask at a time, so a reader that started asking for a long tag
 * by type would fail loudly rather than hand every saved boss its defaults.</p>
 */
class BossAbilityMaskWidthTest {

    /** A builder's choice: everything but the melee swing and the newest kind, which no build ever offered whole. */
    private static final long CHOICE = ~(1L << BossAbilityKind.MELEE) & ~(1L << (BossAbilityKind.COUNT - 1));
    /** A handful of bits, the newest kind's among them. */
    private static final long HANDFUL = 1L << BossAbilityKind.AREA | 1L << BossAbilityKind.HOOK
            | 1L << (BossAbilityKind.COUNT - 1);
    /** A bit past every kind there is: only a long can hold it, and only the list can drop it. */
    private static final long PAST_THE_LIST = 1L << 40;

    /**
     * One saved mask: the key it is written under, the stamp written beside it in the same
     * width if there is one, the bits its reader keeps, a tag as a fresh owner saves it, and
     * the mask a fresh owner reports after reading that tag back.
     */
    private record Mask(String key, String stamp, long all,
                        Supplier<CompoundTag> saved, ToLongFunction<CompoundTag> reread) {
    }

    private static final List<Mask> MASKS = List.of(
            new Mask("CastRootMask", null, BossPhaseData.CAST_ROOT_ALL,
                    BossAbilityMaskWidthTest::phaseTag, tag -> bitsOf(phase(tag)::isCastRooted)),
            new Mask("FinishMask", null, BossAbilityKind.FINISH_ALL,
                    BossAbilityMaskWidthTest::phaseTag, tag -> bitsOf(phase(tag)::waitsForFinish)),
            new Mask("ShadowAbilities", null, BossShadowSettings.COPY_ALL,
                    BossAbilityMaskWidthTest::phaseTag, tag -> phase(tag).shadow().getAbilities()),
            new Mask("GeckoBossTelegraphAbilities", "GeckoBossTelegraphAbilitiesKnown",
                    TeleportPathData.TELEGRAPH_ALL_ABILITIES,
                    BossAbilityMaskWidthTest::bossTag, tag -> boss(tag).getTelegraphAbilities()),
            new Mask("GeckoNpcImmuneAbilities", null, (1L << BossAbilityKind.COUNT) - 1,
                    () -> new NpcImmunityData().writeToNBT(new CompoundTag()),
                    tag -> immunity(tag).getImmuneAbilities()),
            new Mask("VulnerabilityMask", null, (1L << BossAbilityKind.COUNT) - 1,
                    () -> new BossTotemEntry(1).writeToNBT(),
                    tag -> BossTotemEntry.readFromNBT(tag, 1).getVulnerabilityMask()));

    @TestFactory
    @DisplayName("an int tag written by an older jar reads back as the same mask")
    Stream<DynamicTest> anIntTagReadsAsTheSameMask() {
        return MASKS.stream().flatMap(mask -> Stream.of(CHOICE, HANDFUL).map(pattern -> {
            long value = pattern & mask.all();
            return DynamicTest.dynamicTest(mask.key() + " holding " + Long.toBinaryString(value), () -> {
                CompoundTag tag = mask.saved().get();
                // toIntExact rather than a cast: a mask that no longer fits an int is one an older
                // jar could never have written, and this case would have nothing left to say.
                tag.putInt(mask.key(), Math.toIntExact(value));
                if (mask.stamp() != null) {
                    tag.putInt(mask.stamp(), Math.toIntExact(tag.getLong(mask.stamp())));
                }
                assertEquals(value, mask.reread().applyAsLong(tag), mask.key() + " read back wrong from an int tag");
            });
        }));
    }

    @TestFactory
    @DisplayName("a long tag reads back as the same mask, and a bit past the list is dropped rather than wrapped")
    Stream<DynamicTest> aLongTagReadsAsTheSameMask() {
        return MASKS.stream().flatMap(mask -> Stream.of(CHOICE & mask.all(), HANDFUL, HANDFUL | PAST_THE_LIST)
                .map(value -> DynamicTest.dynamicTest(mask.key() + " holding " + Long.toBinaryString(value), () -> {
                    CompoundTag tag = mask.saved().get();
                    tag.putLong(mask.key(), value);
                    assertEquals(value & mask.all(), mask.reread().applyAsLong(tag),
                            mask.key() + " read back wrong from a long tag");
                })));
    }

    @TestFactory
    @DisplayName("every mask is written as a long tag")
    Stream<DynamicTest> everyMaskIsWrittenAsALongTag() {
        return MASKS.stream().map(mask -> DynamicTest.dynamicTest(mask.key(), () -> {
            CompoundTag tag = mask.saved().get();
            assertEquals(Tag.TAG_LONG, tag.get(mask.key()).getId(), mask.key() + " is not saved as a long");
            if (mask.stamp() != null) {
                assertEquals(Tag.TAG_LONG, tag.get(mask.stamp()).getId(), mask.stamp() + " is not saved as a long");
            }
        }));
    }

    @Test
    @DisplayName("the tag layer reads an int tag as a long, and hands an older jar the low bits of a long tag")
    void theTagLayerReadsEitherWidth() {
        CompoundTag tag = new CompoundTag();
        int older = Math.toIntExact(HANDFUL);
        tag.putInt("CastRootMask", older);
        assertEquals(HANDFUL, tag.getLong("CastRootMask"), "an int tag reads as the same number through getLong");
        tag.putInt("CastRootMask", -1);
        assertEquals(-1L, tag.getLong("CastRootMask"), "sign and all, which each reader's own full mask then cuts down");
        tag.putLong("CastRootMask", HANDFUL | PAST_THE_LIST);
        assertEquals(HANDFUL | PAST_THE_LIST, tag.getLong("CastRootMask"), "a long tag keeps its high bits");
        assertEquals(older, tag.getInt("CastRootMask"),
                "and an older jar reading it through getInt still gets every bit below thirty-two");
    }

    @Test
    @DisplayName("the list stays inside a long mask with the sign bit spare, and every full mask inside the list")
    void theListFitsTheMask() {
        assertTrue(BossAbilityKind.COUNT <= Long.SIZE - 1,
                "the guard in BossAbilityKind refuses a list of " + BossAbilityKind.COUNT);
        for (Mask mask : MASKS) {
            assertTrue(mask.all() >= 0, mask.key() + "'s full mask reaches the sign bit");
            assertEquals(0L, mask.all() >>> BossAbilityKind.COUNT, mask.key() + "'s full mask has a bit past the list");
        }
        assertEquals(0L, BossAbilityKind.LASTING_ALL >>> BossAbilityKind.COUNT, "the lasting list has a bit past the list");
        assertEquals(0L, BossAbilityKind.COMBO_ALL >>> BossAbilityKind.COUNT, "the chain list has a bit past the list");
    }

    private static CompoundTag phaseTag() {
        return new BossPhaseData().writeToNBT();
    }

    private static BossPhaseData phase(CompoundTag tag) {
        BossPhaseData phase = new BossPhaseData();
        phase.readFromNBT(tag);
        return phase;
    }

    private static CompoundTag bossTag() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        return data.writeToNBT(new CompoundTag());
    }

    private static TeleportPathData boss(CompoundTag tag) {
        TeleportPathData data = new TeleportPathData();
        data.readFromNBT(tag);
        return data;
    }

    private static NpcImmunityData immunity(CompoundTag tag) {
        NpcImmunityData data = new NpcImmunityData();
        data.readFromNBT(tag);
        return data;
    }

    /** The mask an owner reports through its per-kind accessor, one bit per kind of the list. */
    private static long bitsOf(IntPredicate marked) {
        long mask = 0L;
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            if (marked.test(kind)) {
                mask |= 1L << kind;
            }
        }
        return mask;
    }
}
