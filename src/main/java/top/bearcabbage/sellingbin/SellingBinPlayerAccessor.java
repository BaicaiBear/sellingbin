package top.bearcabbage.sellingbin;

public interface SellingBinPlayerAccessor {
    void saveSellingData();

    SellingBinInventory getSellingBinInventory();
}
