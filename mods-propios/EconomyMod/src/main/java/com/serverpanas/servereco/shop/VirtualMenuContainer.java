package com.serverpanas.servereco.shop;

import net.minecraft.world.SimpleContainer;

/**
 * Container backing a server-side virtual menu. Its slots are hidden from
 * Inventory Sorter's mapping after ChestMenu validates the 54-slot container.
 */
public final class VirtualMenuContainer extends SimpleContainer {

    private boolean excludedFromSorting;

    public VirtualMenuContainer(int size) {
        super(size);
    }

    public void excludeFromSorting() {
        excludedFromSorting = true;
    }

    @Override
    public int getContainerSize() {
        return excludedFromSorting ? 0 : super.getContainerSize();
    }
}
