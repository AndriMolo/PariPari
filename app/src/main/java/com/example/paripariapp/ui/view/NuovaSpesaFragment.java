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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Schermata a schermo intero (Fragment) per la creazione di una nuova spesa.
 * Estende BaseSpesaFragment per condividere la gestione delle 3 modalità di divisione,
 * dell'auto-bilanciamento in tempo reale e della validazione.
 */
public class NuovaSpesaFragment extends BaseSpesaFragment {

    private FragmentNuovaSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;

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
            if (!nomi.isEmpty() && (binding.menuPagante.getText() == null || binding.menuPagante.getText().toString().isEmpty())) {
                binding.menuPagante.setText(nomi.get(0), false);
            }
            binding.menuPagante.setOnClickListener(v -> binding.menuPagante.showDropDown());

            popolaRighePartecipantiComuni(lista, null, null, 0.0);
            cambiaTipoDivisione(tipoDivisione);
        });
    }

    private void setupSalva() {
        binding.azioneSalva.setOnClickListener(v -> {
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
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}