package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.util.AppSnackbar;
import com.example.paripariapp.util.ImportoUtil;
import com.example.paripariapp.util.CalcolatriceEspressioniUtil;
import com.example.paripariapp.util.CategoriaUtil;
import com.example.paripariapp.util.DecimalDigitsInputFilter;
import com.example.paripariapp.util.KeyboardUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fragment base per la gestione della scheda spese (creazione e modifica).
 * Incapsula l'intera logica di presentazione, gestione delle modalità di divisione
 * (Equa, Percentuale, Per parti), auto-bilanciamento in tempo reale, tap-to-clear
 * e lifecycle cleanup per prevenire memory leak.
 */
public abstract class BaseSpesaFragment extends Fragment {

    protected static final String ARG_SCHEDA_ID = "arg_scheda_id";
    protected static final String ARG_VALUTA = "arg_valuta";

    private static final InputFilter[] FILTRO_DUE_DECIMALI = new InputFilter[]{new DecimalDigitsInputFilter(2)};

    protected String schedaId;
    protected String valuta;
    protected String valutaScheda;
    protected List<Partecipante> partecipanti = new ArrayList<>();

    protected SpesaUiHelper.TipoDivisione tipoDivisione = SpesaUiHelper.TipoDivisione.EQUA;

    protected final Map<String, CheckBox> checkMap = new HashMap<>();
    protected final Map<String, EditText> quotaInputMap = new HashMap<>();
    protected final Map<String, TextInputLayout> quotaContainerMap = new HashMap<>();
    protected final Map<String, TextView> quotaEquaTextMap = new HashMap<>();

    protected final Set<String> lockedParticipantIds = new HashSet<>();
    protected Map<String, Double> percentValuesMap = new HashMap<>();
    protected Map<String, Double> importoValuesMap = new HashMap<>();
    protected boolean isUpdatingText = false;
    protected boolean isClearingOnFocus = false;

    // Riferimenti alle viste comuni
    protected MaterialToolbar toolbar;
    protected TextInputEditText campoDescrizione;
    protected TextInputEditText campoImporto;
    protected TextInputEditText campoValuta;
    protected TextInputEditText campoData;
    protected AutoCompleteTextView menuPagante;
    protected AutoCompleteTextView menuCategoria;
    protected MaterialButtonToggleGroup toggleGruppoDivisione;
    protected LinearLayout layoutElencoQuote;
    protected View azioneSalva;

    protected long dataSelezionataTimestamp = System.currentTimeMillis();
    protected boolean categoriaSelezionataManualmente = false;

    public static class DatiFormValidi {
        public final String titolo;
        public final double importo;
        public final String pagatoreId;
        public final String categoria;
        public final List<Partecipante> partecipantiInclusi;
        public final List<SpesaPartecipante> quoteCalcolate;
        public final long timestamp;

        public DatiFormValidi(
                String titolo,
                double importo,
                String pagatoreId,
                String categoria,
                List<Partecipante> partecipantiInclusi,
                List<SpesaPartecipante> quoteCalcolate,
                long timestamp
        ) {
            this.titolo = titolo;
            this.importo = importo;
            this.pagatoreId = pagatoreId;
            this.categoria = categoria;
            this.partecipantiInclusi = partecipantiInclusi;
            this.quoteCalcolate = quoteCalcolate;
            this.timestamp = timestamp;
        }

        public DatiFormValidi(
                String titolo,
                double importo,
                String pagatoreId,
                String categoria,
                List<Partecipante> partecipantiInclusi,
                List<SpesaPartecipante> quoteCalcolate
        ) {
            this(titolo, importo, pagatoreId, categoria, partecipantiInclusi, quoteCalcolate, System.currentTimeMillis());
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            schedaId = getArguments().getString(ARG_SCHEDA_ID);
            valuta = getArguments().getString(ARG_VALUTA);
            valutaScheda = valuta;
        }
    }

    /**
     * Inizializza i componenti grafici comuni e imposta i listener di base.
     */
    protected void initCommonViews(
            @NonNull MaterialToolbar toolbar,
            @NonNull TextInputEditText campoDescrizione,
            @NonNull TextInputEditText campoImporto,
            @NonNull TextInputEditText campoValuta,
            @Nullable TextInputEditText campoData,
            @NonNull AutoCompleteTextView menuPagante,
            @NonNull AutoCompleteTextView menuCategoria,
            @NonNull MaterialButtonToggleGroup toggleGruppoDivisione,
            @NonNull LinearLayout layoutElencoQuote,
            @NonNull View azioneSalva
    ) {
        this.toolbar = toolbar;
        this.campoDescrizione = campoDescrizione;
        this.campoImporto = campoImporto;
        this.campoValuta = campoValuta;
        this.campoData = campoData;
        this.menuPagante = menuPagante;
        this.menuCategoria = menuCategoria;
        this.toggleGruppoDivisione = toggleGruppoDivisione;
        this.layoutElencoQuote = layoutElencoQuote;
        this.azioneSalva = azioneSalva;

        toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        campoValuta.setText(getValutaEffettiva());
        campoValuta.setFocusable(false);
        campoValuta.setClickable(true);
        campoValuta.setOnClickListener(v -> {
            SelettoreValutaBottomSheet sheet = SelettoreValutaBottomSheet.newInstance(getValutaEffettiva());
            sheet.setOnCurrencySelectedListener(currencyFull -> {
                String nuovaValuta = com.example.paripariapp.data.repository.UserPreferencesRepository.extractCurrencyCode(currencyFull);
                valuta = nuovaValuta;
                campoValuta.setText(valuta);
                if (tipoDivisione == SpesaUiHelper.TipoDivisione.EQUA) {
                    ricalcolaQuoteEqua();
                } else if (tipoDivisione == SpesaUiHelper.TipoDivisione.PER_PARTI) {
                    aggiornaImportiSuCambioTotale();
                }
            });
            sheet.show(getParentFragmentManager(), "selettore_valuta_spesa");
        });

        if (this.campoData != null) {
            impostaData(dataSelezionataTimestamp);
            this.campoData.setFocusable(false);
            this.campoData.setClickable(true);
            this.campoData.setOnClickListener(v -> apriSelettoreData());
        }

        // Auto-switch intelligente categoria in base al titolo digitato
        campoDescrizione.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (categoriaSelezionataManualmente || menuCategoria == null || getContext() == null) {
                    return;
                }
                String test = s != null ? s.toString() : "";
                String indovinataStd = CategoriaUtil.indovinaCategoriaDaTitolo(test);
                if (indovinataStd != null) {
                    String indovinata = CategoriaUtil.getNomeLocalizzatoCategoria(requireContext(), indovinataStd);
                    String corrente = menuCategoria.getText() != null ? menuCategoria.getText().toString() : "";
                    if (!indovinata.equalsIgnoreCase(corrente)) {
                        menuCategoria.setText(indovinata, false);
                    }
                } else if (test.trim().isEmpty()) {
                    String altroLoc = getString(R.string.cat_altro);
                    String corrente = menuCategoria.getText() != null ? menuCategoria.getText().toString() : "";
                    if (!corrente.equalsIgnoreCase(altroLoc)) {
                        menuCategoria.setText(altroLoc, false);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        campoImporto.setFilters(FILTRO_DUE_DECIMALI);
        campoImporto.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tipoDivisione == SpesaUiHelper.TipoDivisione.EQUA) {
                    ricalcolaQuoteEqua();
                } else if (tipoDivisione == SpesaUiHelper.TipoDivisione.PER_PARTI) {
                    aggiornaImportiSuCambioTotale();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Calcolatore inline al termine della digitazione/cambio focus o azione Done da tastiera
        campoImporto.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                valutaEspressioneImportoSeNecessario();
            }
        });
        campoImporto.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                valutaEspressioneImportoSeNecessario();
                campoImporto.clearFocus();
                KeyboardUtil.hideKeyboard(campoImporto);
                return true;
            }
            return false;
        });

        setupCategorieDropdown();
        setupToggleDivisione();
    }

    public void impostaData(long timestamp) {
        this.dataSelezionataTimestamp = timestamp;
        if (campoData != null) {
            campoData.setText(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(new Date(timestamp)));
        }
    }

    protected void apriSelettoreData() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.label_data_spesa)
                .setSelection(dataSelezionataTimestamp)
                .build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null) {
                impostaData(selection);
            }
        });
        datePicker.show(getParentFragmentManager(), "date_picker_spesa");
    }

    protected void valutaEspressioneImportoSeNecessario() {
        if (campoImporto == null) return;
        String raw = campoImporto.getText() != null ? campoImporto.getText().toString().trim() : "";
        if (CalcolatriceEspressioniUtil.contieneOperatori(raw)) {
            Double calcolato = CalcolatriceEspressioniUtil.valuta(raw);
            if (calcolato != null) {
                campoImporto.setText(String.format(Locale.US, "%.2f", calcolato));
            }
        }
    }

    @NonNull
    protected String getValutaEffettiva() {
        return valuta != null && !valuta.isEmpty() ? valuta : getString(R.string.valuta_default);
    }

    @NonNull
    protected String getValutaScheda() {
        return valutaScheda != null && !valutaScheda.isEmpty() ? valutaScheda : getString(R.string.valuta_default);
    }

    protected void setupCategorieDropdown() {
        if (menuCategoria == null) return;
        String[] categorie = getResources().getStringArray(R.array.categorie_spesa);
        ArrayAdapter<String> adapter = SpesaUiHelper.creaDropdownAdapter(requireContext(), Arrays.asList(categorie));
        menuCategoria.setAdapter(adapter);
        if (menuCategoria.getText() == null || menuCategoria.getText().toString().isEmpty()) {
            menuCategoria.setText(categorie[0], false);
        }
        menuCategoria.setOnItemClickListener((parent, view, position, id) -> {
            categoriaSelezionataManualmente = true;
        });
        menuCategoria.setOnClickListener(v -> menuCategoria.showDropDown());
    }

    protected void setupToggleDivisione() {
        if (toggleGruppoDivisione == null) return;
        toggleGruppoDivisione.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_divisione_equa) {
                cambiaTipoDivisione(SpesaUiHelper.TipoDivisione.EQUA);
            } else if (checkedId == R.id.btn_divisione_percentuale) {
                cambiaTipoDivisione(SpesaUiHelper.TipoDivisione.PERCENTUALE);
            } else if (checkedId == R.id.btn_divisione_parti) {
                cambiaTipoDivisione(SpesaUiHelper.TipoDivisione.PER_PARTI);
            }
        });
    }

    protected double getImportoTotale() {
        if (campoImporto == null || campoImporto.getText() == null) return 0.0;
        return SpesaUiHelper.parseImporto(campoImporto.getText().toString());
    }

    @NonNull
    protected List<Partecipante> getPartecipantiInclusi() {
        List<Partecipante> inclusi = new ArrayList<>();
        for (Partecipante p : partecipanti) {
            if (p.isAttivo()) {
                CheckBox cb = checkMap.get(p.getId());
                if (cb != null && cb.isChecked()) {
                    inclusi.add(p);
                }
            }
        }
        return inclusi;
    }

    protected void cambiaTipoDivisione(@NonNull SpesaUiHelper.TipoDivisione nuovoTipo) {
        this.tipoDivisione = nuovoTipo;
        lockedParticipantIds.clear();

        String valutaStr = getValutaEffettiva();
        List<Partecipante> inclusi = getPartecipantiInclusi();

        if (nuovoTipo == SpesaUiHelper.TipoDivisione.EQUA) {
            for (Partecipante p : partecipanti) {
                TextView tvEqua = quotaEquaTextMap.get(p.getId());
                TextInputLayout til = quotaContainerMap.get(p.getId());
                if (tvEqua != null) tvEqua.setVisibility(View.VISIBLE);
                if (til != null) til.setVisibility(View.GONE);
            }
            ricalcolaQuoteEqua();
        } else if (nuovoTipo == SpesaUiHelper.TipoDivisione.PERCENTUALE) {
            for (Partecipante p : partecipanti) {
                TextView tvEqua = quotaEquaTextMap.get(p.getId());
                TextInputLayout til = quotaContainerMap.get(p.getId());
                if (tvEqua != null) tvEqua.setVisibility(View.GONE);
                if (til != null) {
                    til.setVisibility(View.VISIBLE);
                    til.setSuffixText("%");
                }
            }
            if (percentValuesMap.isEmpty()) {
                percentValuesMap = SpesaUiHelper.calcolaDivisioneInizialePercentuale(inclusi);
            }
            popolaCampiPercentuale();
        } else if (nuovoTipo == SpesaUiHelper.TipoDivisione.PER_PARTI) {
            for (Partecipante p : partecipanti) {
                TextView tvEqua = quotaEquaTextMap.get(p.getId());
                TextInputLayout til = quotaContainerMap.get(p.getId());
                if (tvEqua != null) tvEqua.setVisibility(View.GONE);
                if (til != null) {
                    til.setVisibility(View.VISIBLE);
                    til.setSuffixText(valutaStr);
                }
            }
            if (importoValuesMap.isEmpty()) {
                importoValuesMap = SpesaUiHelper.calcolaDivisioneInizialeImporto(getImportoTotale(), inclusi);
            }
            popolaCampiImporto();
        }
    }

    protected void ricalcolaQuoteEqua() {
        double totale = getImportoTotale();
        List<Partecipante> inclusi = getPartecipantiInclusi();
        String valutaStr = getValutaEffettiva();
        double quotaSingola = (!inclusi.isEmpty() && totale > 0) ? (totale / inclusi.size()) : 0.0;

        for (Partecipante p : partecipanti) {
            TextView tv = quotaEquaTextMap.get(p.getId());
            CheckBox cb = checkMap.get(p.getId());
            if (tv == null) continue;

            if (cb != null && cb.isChecked()) {
                tv.setText(ImportoUtil.formatta(quotaSingola, valutaStr));
                if (getContext() != null) {
                    tv.setTextColor(requireContext().getColor(R.color.md_theme_primary));
                }
            } else {
                tv.setText(R.string.spesa_escluso);
                if (getContext() != null) {
                    tv.setTextColor(requireContext().getColor(android.R.color.darker_gray));
                }
            }
        }
    }

    protected void popolaCampiPercentuale() {
        isUpdatingText = true;
        for (Partecipante p : partecipanti) {
            EditText et = quotaInputMap.get(p.getId());
            CheckBox cb = checkMap.get(p.getId());
            if (et == null) continue;

            if (cb != null && cb.isChecked()) {
                Double val = percentValuesMap.get(p.getId());
                et.setText(String.format(Locale.US, "%.1f", val != null ? val : 0.0));
                et.setEnabled(true);
            } else {
                et.setText(String.format(Locale.US, "%.1f", 0.0));
                et.setEnabled(false);
            }
        }
        isUpdatingText = false;
    }

    protected void popolaCampiImporto() {
        isUpdatingText = true;
        for (Partecipante p : partecipanti) {
            EditText et = quotaInputMap.get(p.getId());
            CheckBox cb = checkMap.get(p.getId());
            if (et == null) continue;

            if (cb != null && cb.isChecked()) {
                Double val = importoValuesMap.get(p.getId());
                et.setText(String.format(Locale.US, "%.2f", val != null ? val : 0.0));
                et.setEnabled(true);
            } else {
                et.setText(String.format(Locale.US, "%.2f", 0.0));
                et.setEnabled(false);
            }
        }
        isUpdatingText = false;
    }

    protected void aggiornaImportiSuCambioTotale() {
        double totale = getImportoTotale();
        List<Partecipante> inclusi = getPartecipantiInclusi();
        if (lockedParticipantIds.isEmpty()) {
            importoValuesMap = SpesaUiHelper.calcolaDivisioneInizialeImporto(totale, inclusi);
            popolaCampiImporto();
        } else {
            String lastLocked = lockedParticipantIds.iterator().next();
            Double val = importoValuesMap.get(lastLocked);
            importoValuesMap = SpesaUiHelper.bilanciaImporti(totale, inclusi, lastLocked, val != null ? val : 0.0, lockedParticipantIds, importoValuesMap);
            popolaCampiImporto();
        }
    }

    /**
     * Gonfia e associa la lista dinamica dei partecipanti nel layout.
     */
    protected void popolaRighePartecipantiComuni(
            @NonNull List<Partecipante> lista,
            @Nullable Set<String> initiallyIncludedIds,
            @Nullable Map<String, Double> customQuoteMap,
            double importoTotaleEsistente
    ) {
        if (layoutElencoQuote == null) return;
        layoutElencoQuote.removeAllViews();
        checkMap.clear();
        quotaInputMap.clear();
        quotaContainerMap.clear();
        quotaEquaTextMap.clear();
        lockedParticipantIds.clear();

        for (Partecipante p : lista) {
            if (!p.isAttivo()) continue;
            com.example.paripariapp.databinding.ItemQuotaPartecipanteBinding rowBinding =
                    com.example.paripariapp.databinding.ItemQuotaPartecipanteBinding.inflate(getLayoutInflater(), layoutElencoQuote, false);
            CheckBox cb = rowBinding.spuntaPartecipante;
            TextView tvNome = rowBinding.nomePartecipante;
            TextView tvEqua = rowBinding.tvQuotaEqua;
            EditText etQuota = rowBinding.campoQuota;
            TextInputLayout quotaContainer = rowBinding.contenitoreQuota;

            tvNome.setText(p.getNome());
            etQuota.setFilters(FILTRO_DUE_DECIMALI);

            boolean isIncluso;
            if (initiallyIncludedIds == null) {
                isIncluso = true;
            } else {
                isIncluso = initiallyIncludedIds.contains(p.getId()) || initiallyIncludedIds.isEmpty();
            }
            cb.setChecked(isIncluso);

            if (customQuoteMap != null && customQuoteMap.containsKey(p.getId()) && importoTotaleEsistente > 0) {
                double quotaVal = customQuoteMap.get(p.getId());
                double percentuale = (quotaVal / importoTotaleEsistente) * 100.0;
                percentValuesMap.put(p.getId(), percentuale);
                importoValuesMap.put(p.getId(), quotaVal);
            } else {
                double percDefault = 100.0 / Math.max(1, lista.size());
                double impDefault = (importoTotaleEsistente > 0 ? importoTotaleEsistente : getImportoTotale()) / Math.max(1, lista.size());
                percentValuesMap.put(p.getId(), percDefault);
                importoValuesMap.put(p.getId(), impDefault);
            }

            checkMap.put(p.getId(), cb);
            quotaInputMap.put(p.getId(), etQuota);
            quotaContainerMap.put(p.getId(), quotaContainer);
            quotaEquaTextMap.put(p.getId(), tvEqua);

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                lockedParticipantIds.clear();
                cambiaTipoDivisione(tipoDivisione);
            });

            etQuota.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    isClearingOnFocus = true;
                    etQuota.setText("");
                    isClearingOnFocus = false;
                } else {
                    String currentText = etQuota.getText() != null ? etQuota.getText().toString().trim() : "";
                    if (currentText.isEmpty()) {
                        isUpdatingText = true;
                        if (tipoDivisione == SpesaUiHelper.TipoDivisione.PERCENTUALE) {
                            Double vMap = percentValuesMap.get(p.getId());
                            etQuota.setText(String.format(Locale.US, "%.1f", vMap != null ? vMap : 0.0));
                        } else if (tipoDivisione == SpesaUiHelper.TipoDivisione.PER_PARTI) {
                            Double vMap = importoValuesMap.get(p.getId());
                            etQuota.setText(String.format(Locale.US, "%.2f", vMap != null ? vMap : 0.0));
                        }
                        isUpdatingText = false;
                    }
                }
            });

            etQuota.setOnClickListener(v -> {
                if (etQuota.hasFocus() && etQuota.getText() != null && !etQuota.getText().toString().isEmpty()) {
                    isClearingOnFocus = true;
                    etQuota.setText("");
                    isClearingOnFocus = false;
                }
            });

            etQuota.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isUpdatingText || isClearingOnFocus || !etQuota.hasFocus()) return;

                    String text = s != null ? s.toString().trim() : "";
                    if (text.isEmpty()) {
                        return; // Il bilanciamento parte solo quando l'utente digita
                    }

                    lockedParticipantIds.add(p.getId());
                    double enteredVal = SpesaUiHelper.parseImporto(text);

                    if (tipoDivisione == SpesaUiHelper.TipoDivisione.PERCENTUALE) {
                        percentValuesMap = SpesaUiHelper.bilanciaPercentuali(
                                getPartecipantiInclusi(), p.getId(), enteredVal, lockedParticipantIds, percentValuesMap);

                        isUpdatingText = true;
                        Double clamped = percentValuesMap.get(p.getId());
                        if (clamped != null && Math.abs(clamped - enteredVal) > 0.05) {
                            etQuota.setText(String.format(Locale.US, "%.1f", clamped));
                            etQuota.setSelection(etQuota.getText().length());
                        }
                        for (Partecipante inc : getPartecipantiInclusi()) {
                            if (!inc.getId().equals(p.getId())) {
                                EditText otherEt = quotaInputMap.get(inc.getId());
                                Double v = percentValuesMap.get(inc.getId());
                                if (otherEt != null) {
                                    otherEt.setText(String.format(Locale.US, "%.1f", v != null ? v : 0.0));
                                }
                            }
                        }
                        isUpdatingText = false;
                    } else if (tipoDivisione == SpesaUiHelper.TipoDivisione.PER_PARTI) {
                        importoValuesMap = SpesaUiHelper.bilanciaImporti(
                                getImportoTotale(), getPartecipantiInclusi(), p.getId(), enteredVal, lockedParticipantIds, importoValuesMap);

                        isUpdatingText = true;
                        Double clamped = importoValuesMap.get(p.getId());
                        if (clamped != null && Math.abs(clamped - enteredVal) > 0.01) {
                            etQuota.setText(String.format(Locale.US, "%.2f", clamped));
                            etQuota.setSelection(etQuota.getText().length());
                        }
                        for (Partecipante inc : getPartecipantiInclusi()) {
                            if (!inc.getId().equals(p.getId())) {
                                EditText otherEt = quotaInputMap.get(inc.getId());
                                Double v = importoValuesMap.get(inc.getId());
                                if (otherEt != null) {
                                    otherEt.setText(String.format(Locale.US, "%.2f", v != null ? v : 0.0));
                                }
                            }
                        }
                        isUpdatingText = false;
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            layoutElencoQuote.addView(rowBinding.getRoot());
        }
    }

    /**
     * Valida i campi del modulo ed estrae i dati pronti per il salvataggio/aggiornamento.
     * Restituisce null se la validazione fallisce.
     */
    @Nullable
    protected DatiFormValidi validaEdEstraiDatiForm(@NonNull String targetSpesaId, int syncStatus) {
        if (campoDescrizione == null || campoImporto == null || menuPagante == null || menuCategoria == null) {
            return null;
        }

        String titolo = campoDescrizione.getText() != null
                ? campoDescrizione.getText().toString().trim() : "";
        String importoStr = campoImporto.getText() != null
                ? campoImporto.getText().toString().trim().replace(",", ".") : "";

        if (titolo.isEmpty()) {
            campoDescrizione.setError(getString(R.string.error_descrizione_spesa));
            return null;
        }

        double importo;
        try {
            if (CalcolatriceEspressioniUtil.contieneOperatori(importoStr)) {
                Double res = CalcolatriceEspressioniUtil.valuta(importoStr);
                if (res == null || res <= 0) throw new NumberFormatException();
                importo = res;
                campoImporto.setText(String.format(Locale.US, "%.2f", importo));
            } else {
                importo = Double.parseDouble(importoStr);
                if (importo <= 0) throw new NumberFormatException();
            }
        } catch (Exception e) {
            campoImporto.setError(getString(R.string.error_importo_spesa));
            return null;
        }

        String nomePagatore = menuPagante.getText().toString();
        String pagatoreId = null;
        for (Partecipante p : partecipanti) {
            if (p.getNome().equals(nomePagatore)) {
                pagatoreId = p.getId();
                break;
            }
        }
        if (pagatoreId == null && !partecipanti.isEmpty()) {
            pagatoreId = partecipanti.get(0).getId();
        }

        String categoria = menuCategoria.getText().toString();
        List<Partecipante> partecipantiInclusi = getPartecipantiInclusi();

        if (partecipantiInclusi.isEmpty()) {
            if (getView() != null) {
                AppSnackbar.show(getView(), R.string.spesa_errore_nessun_partecipante);
            }
            return null;
        }

        List<SpesaPartecipante> quoteCalcolate = new ArrayList<>();
        if (tipoDivisione == SpesaUiHelper.TipoDivisione.EQUA) {
            quoteCalcolate = SpesaUiHelper.calcolaDivisioneEqua(targetSpesaId, importo, partecipantiInclusi, partecipanti, syncStatus);
        } else if (tipoDivisione == SpesaUiHelper.TipoDivisione.PERCENTUALE) {
            quoteCalcolate = SpesaUiHelper.calcolaDivisionePercentuale(targetSpesaId, importo, partecipantiInclusi, partecipanti, percentValuesMap, syncStatus);
        } else if (tipoDivisione == SpesaUiHelper.TipoDivisione.PER_PARTI) {
            quoteCalcolate = SpesaUiHelper.calcolaDivisionePerImporto(targetSpesaId, importo, partecipantiInclusi, partecipanti, importoValuesMap, syncStatus);
        }

        return new DatiFormValidi(titolo, importo, pagatoreId, categoria, partecipantiInclusi, quoteCalcolate, dataSelezionataTimestamp);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Prevenzione di memory leak liberando tutti i riferimenti forti a View trattenuti dalle mappe
        checkMap.clear();
        quotaInputMap.clear();
        quotaContainerMap.clear();
        quotaEquaTextMap.clear();
        toolbar = null;
        campoDescrizione = null;
        campoImporto = null;
        campoValuta = null;
        campoData = null;
        menuPagante = null;
        menuCategoria = null;
        toggleGruppoDivisione = null;
        layoutElencoQuote = null;
        azioneSalva = null;
    }
}
