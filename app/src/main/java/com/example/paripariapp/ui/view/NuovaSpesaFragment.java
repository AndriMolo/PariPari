package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.databinding.FragmentNuovaSpesaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schermata a schermo intero (Fragment) per la creazione di una nuova spesa.
 */
public class NuovaSpesaFragment extends Fragment {

    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_VALUTA = "arg_valuta";

    private FragmentNuovaSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;

    private String schedaId;
    private String valuta;
    private List<Partecipante> partecipanti = new ArrayList<>();

    private final Map<String, CheckBox> checkMap = new HashMap<>();
    private final Map<String, EditText> quotaInputMap = new HashMap<>();

    public static NuovaSpesaFragment newInstance(String schedaId, String valuta) {
        NuovaSpesaFragment fragment = new NuovaSpesaFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SCHEDA_ID, schedaId);
        args.putString(ARG_VALUTA, valuta);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            schedaId = getArguments().getString(ARG_SCHEDA_ID);
            valuta = getArguments().getString(ARG_VALUTA);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNuovaSpesaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        binding.campoValuta.setText(valuta != null ? valuta : getString(R.string.valuta_default));

        setupCategorieDropdown();
        setupSwitchDivisione();
        setupObserverPartecipanti();
        setupSalva();
    }

    private void setupCategorieDropdown() {
        String[] categorie = new String[]{
                getString(R.string.cat_cibo),
                getString(R.string.cat_trasporti),
                getString(R.string.cat_alloggio),
                getString(R.string.cat_svago),
                getString(R.string.cat_altro)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categorie);
        binding.menuCategoria.setAdapter(adapter);
        binding.menuCategoria.setText(categorie[0], false);
    }

    private void setupSwitchDivisione() {
        binding.interruttorePersonalizzata.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (Map.Entry<String, EditText> entry : quotaInputMap.entrySet()) {
                View container = (View) entry.getValue().getParent().getParent();
                container.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void setupObserverPartecipanti() {
        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), lista -> {
            if (lista == null) return;
            this.partecipanti = lista;

            List<String> nomi = new ArrayList<>();
            for (Partecipante p : lista) {
                nomi.add(p.getNome());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, nomi);
            binding.menuPagante.setAdapter(adapter);
            if (!nomi.isEmpty()) {
                binding.menuPagante.setText(nomi.get(0), false);
            }

            popolaRighePartecipanti(lista);
        });
    }

    private void popolaRighePartecipanti(List<Partecipante> lista) {
        binding.layoutElencoQuote.removeAllViews();
        checkMap.clear();
        quotaInputMap.clear();

        for (Partecipante p : lista) {
            View row = getLayoutInflater().inflate(R.layout.item_quota_partecipante, binding.layoutElencoQuote, false);
            CheckBox cb = row.findViewById(R.id.spunta_partecipante);
            TextView tvNome = row.findViewById(R.id.nome_partecipante);
            EditText etQuota = row.findViewById(R.id.campo_quota);
            View quotaContainer = row.findViewById(R.id.contenitore_quota);

            tvNome.setText(p.getNome());
            cb.setChecked(true);
            quotaContainer.setVisibility(binding.interruttorePersonalizzata.isChecked() ? View.VISIBLE : View.GONE);

            checkMap.put(p.getId(), cb);
            quotaInputMap.put(p.getId(), etQuota);

            binding.layoutElencoQuote.addView(row);
        }
    }

    private void setupSalva() {
        binding.azioneSalva.setOnClickListener(v -> {
            String titolo = binding.campoDescrizione.getText() != null
                    ? binding.campoDescrizione.getText().toString().trim() : "";
            String importoStr = binding.campoImporto.getText() != null
                    ? binding.campoImporto.getText().toString().trim().replace(",", ".") : "";

            if (titolo.isEmpty()) {
                binding.campoDescrizione.setError(getString(R.string.error_descrizione_spesa));
                return;
            }

            double importo;
            try {
                importo = Double.parseDouble(importoStr);
                if (importo <= 0) throw new NumberFormatException();
            } catch (Exception e) {
                binding.campoImporto.setError(getString(R.string.error_importo_spesa));
                return;
            }

            String nomePagatore = binding.menuPagante.getText().toString();
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

            String categoria = binding.menuCategoria.getText().toString();
            List<SpesaPartecipante> quote = new ArrayList<>();
            String spesaId = java.util.UUID.randomUUID().toString();

            if (!binding.interruttorePersonalizzata.isChecked()) {
                List<Partecipante> partecipantiInclusi = new ArrayList<>();
                for (Partecipante p : partecipanti) {
                    CheckBox cb = checkMap.get(p.getId());
                    if (cb != null && cb.isChecked()) {
                        partecipantiInclusi.add(p);
                    }
                }

                if (partecipantiInclusi.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.spesa_errore_nessun_partecipante, Toast.LENGTH_SHORT).show();
                    return;
                }

                double quotaSingola = importo / partecipantiInclusi.size();
                for (Partecipante p : partecipantiInclusi) {
                    quote.add(new SpesaPartecipante(spesaId, p.getId(), quotaSingola, SyncStatus.PENDING_INSERT));
                }
            } else {
                double sommaQuote = 0.0;
                for (Partecipante p : partecipanti) {
                    CheckBox cb = checkMap.get(p.getId());
                    if (cb != null && cb.isChecked()) {
                        EditText et = quotaInputMap.get(p.getId());
                        double q = 0.0;
                        try {
                            if (et != null && et.getText() != null && !et.getText().toString().isEmpty()) {
                                q = Double.parseDouble(et.getText().toString().replace(",", "."));
                            }
                        } catch (Exception ignored) {}
                        sommaQuote += q;
                        quote.add(new SpesaPartecipante(spesaId, p.getId(), q, SyncStatus.PENDING_INSERT));
                    }
                }

                if (Math.abs(sommaQuote - importo) > 0.05) {
                    Toast.makeText(requireContext(), getString(R.string.spesa_errore_somma_quote, sommaQuote, importo), Toast.LENGTH_LONG).show();
                    return;
                }
            }

            Spesa spesa = new Spesa(
                    spesaId,
                    schedaId,
                    titolo,
                    importo,
                    valuta != null ? valuta : getString(R.string.valuta_default),
                    System.currentTimeMillis(),
                    categoria,
                    pagatoreId,
                    null,
                    SyncStatus.PENDING_INSERT
            );

            viewModel.inserisciSpesaConQuote(spesa, quote);
            Toast.makeText(requireContext(), R.string.msg_spesa_aggiunta, Toast.LENGTH_SHORT).show();

            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}