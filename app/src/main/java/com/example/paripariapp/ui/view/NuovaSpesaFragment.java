package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SyncStatus;
import com.example.paripariapp.databinding.FragmentNuovaSpesaBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Schermata a schermo intero (Fragment) per la creazione di una nuova spesa o rimborso (Tricount-style).
 */
public class NuovaSpesaFragment extends BaseSpesaFragment {

    private FragmentNuovaSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;
    private boolean isRimborso = false;

    public static NuovaSpesaFragment newInstance(String schedaId, String valuta) {
        NuovaSpesaFragment fragment = new NuovaSpesaFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SCHEDA_ID, schedaId);
        args.putString(ARG_VALUTA, valuta);
        fragment.setArguments(args);
        return fragment;
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

        initCommonViews(
                binding.toolbarNuovaSpesa,
                binding.campoDescrizione,
                binding.campoImporto,
                binding.campoValuta,
                binding.menuPagante,
                binding.menuCategoria,
                binding.toggleGruppoDivisione,
                binding.layoutElencoQuote,
                binding.azioneSalva
        );

        binding.toggleGruppoDivisione.check(R.id.btn_divisione_equa);

        binding.toggleTipoOperazione.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            isRimborso = (checkedId == R.id.btn_tipo_rimborso);
            if (isRimborso) {
                binding.contenitoreDestinatario.setVisibility(View.VISIBLE);
                binding.contenitoreCategoria.setVisibility(View.GONE);
                binding.sezioneDivisione.setVisibility(View.GONE);
                if (binding.campoDescrizione.getText() == null || binding.campoDescrizione.getText().toString().isEmpty()) {
                    binding.campoDescrizione.setText("Rimborso");
                }
            } else {
                binding.contenitoreDestinatario.setVisibility(View.GONE);
                binding.contenitoreCategoria.setVisibility(View.VISIBLE);
                binding.sezioneDivisione.setVisibility(View.VISIBLE);
            }
        });

        setupObserverPartecipanti();
        setupSalva();
    }

    private void setupObserverPartecipanti() {
        viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), lista -> {
            if (lista == null) return;
            this.partecipanti = lista;

            final List<String> nomi = new ArrayList<>();
            for (Partecipante p : lista) {
                nomi.add(p.getNome());
            }
            ArrayAdapter<String> adapter = SpesaUiHelper.creaDropdownAdapter(requireContext(), nomi);
            binding.menuPagante.setAdapter(adapter);
            binding.menuDestinatario.setAdapter(adapter);

            if (!nomi.isEmpty() && (binding.menuPagante.getText() == null || binding.menuPagante.getText().toString().isEmpty())) {
                binding.menuPagante.setText(nomi.get(0), false);
            }
            if (nomi.size() > 1 && (binding.menuDestinatario.getText() == null || binding.menuDestinatario.getText().toString().isEmpty())) {
                binding.menuDestinatario.setText(nomi.get(1), false);
            } else if (!nomi.isEmpty() && (binding.menuDestinatario.getText() == null || binding.menuDestinatario.getText().toString().isEmpty())) {
                binding.menuDestinatario.setText(nomi.get(0), false);
            }

            binding.menuPagante.setOnClickListener(v -> binding.menuPagante.showDropDown());
            binding.menuDestinatario.setOnClickListener(v -> binding.menuDestinatario.showDropDown());

            popolaRighePartecipantiComuni(lista, null, null, 0.0);
            cambiaTipoDivisione(tipoDivisione);
        });
    }

    private void setupSalva() {
        binding.azioneSalva.setOnClickListener(v -> {
            double importo = getImportoTotale();
            if (importo <= 0) {
                binding.campoImporto.setError("Inserisci un importo valido");
                return;
            }

            if (isRimborso) {
                String paganteNome = binding.menuPagante.getText() != null ? binding.menuPagante.getText().toString() : "";
                String destinatarioNome = binding.menuDestinatario.getText() != null ? binding.menuDestinatario.getText().toString() : "";

                if (paganteNome.equals(destinatarioNome)) {
                    Toast.makeText(requireContext(), "Il mittente e il destinatario non possono coincidere", Toast.LENGTH_SHORT).show();
                    return;
                }

                String daId = null;
                String aId = null;
                for (Partecipante p : partecipanti) {
                    if (p.getNome().equalsIgnoreCase(paganteNome)) daId = p.getId();
                    if (p.getNome().equalsIgnoreCase(destinatarioNome)) aId = p.getId();
                }

                if (daId != null && aId != null) {
                    SpeseViewModel speseVm = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);
                    speseVm.registraPagamento(schedaId, daId, paganteNome, aId, destinatarioNome, importo, getValutaEffettiva());
                    Toast.makeText(requireContext(), R.string.msg_operazione_completata, Toast.LENGTH_SHORT).show();
                    if (getParentFragmentManager() != null) {
                        getParentFragmentManager().popBackStack();
                    }
                }
            } else {
                String spesaId = UUID.randomUUID().toString();
                DatiFormValidi dati = validaEdEstraiDatiForm(spesaId, SyncStatus.PENDING_INSERT);
                if (dati == null) return;

                Spesa spesa = new Spesa(
                        spesaId,
                        schedaId,
                        dati.titolo,
                        dati.importo,
                        getValutaEffettiva(),
                        System.currentTimeMillis(),
                        dati.categoria,
                        dati.pagatoreId,
                        null,
                        SyncStatus.PENDING_INSERT
                );

                viewModel.inserisciSpesaConQuote(spesa, dati.quoteCalcolate);
                Toast.makeText(requireContext(), R.string.msg_spesa_aggiunta, Toast.LENGTH_SHORT).show();

                if (getParentFragmentManager() != null) {
                    getParentFragmentManager().popBackStack();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
