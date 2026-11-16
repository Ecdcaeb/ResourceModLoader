package rml.loader.content.presentation;

import net.minecraft.item.ItemRecord;
import net.minecraft.util.SoundEvent;

/** Public construction bridge for data-pack registered jukebox records. */
final class PresentationRecordItem extends ItemRecord {
    PresentationRecordItem(String name, SoundEvent sound) {
        super(name, sound);
    }
}
