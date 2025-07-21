package top.bearcabbage.sellingbin;

import java.io.Serializable;

public class PlayerInventory implements Serializable {
    private final ImplementedInventory woodenBin;

    public PlayerInventory (ImplementedInventory inventory) {
        woodenBin = inventory;
    }

    public PlayerInventory () {
        woodenBin = ImplementedInventory.ofSize(27);
    }

    public ImplementedInventory getWoodenBin() {
        return woodenBin;
    }
}