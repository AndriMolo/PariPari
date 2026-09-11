package com.example.paripariapp.data.model;

/**
 * Modello wrapper per gestire righe eterogenee nel RecyclerView:
 * separa i titoli delle date (header) dalle singole transazioni (item).
 */
public class SpesaListItem {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;

    private final int type;
    private final String headerTitle;
    private final SpesaConDettagli spesa;

    // Costruttore per l'intestazione data (es. "11 Settembre 2026")
    public SpesaListItem(String headerTitle) {
        this.type = TYPE_HEADER;
        this.headerTitle = headerTitle;
        this.spesa = null;
    }

    // Costruttore per la singola spesa
    public SpesaListItem(SpesaConDettagli spesa) {
        this.type = TYPE_ITEM;
        this.headerTitle = null;
        this.spesa = spesa;
    }

    public int getType() {
        return type;
    }

    public String getHeaderTitle() {
        return headerTitle;
    }

    public SpesaConDettagli getSpesa() {
        return spesa;
    }
}