package net.joe.sellingbin;

import java.io.Serializable;

public class PlayerInventory implements Serializable {
    private final ImplementedInventory woodenBin = ImplementedInventory.ofSize(27);

    public ImplementedInventory getWoodenBin() {
        return woodenBin;
    }
}