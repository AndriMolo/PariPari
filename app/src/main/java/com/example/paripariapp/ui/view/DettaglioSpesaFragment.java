package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.data.model.Spesa;
import com.example.paripariapp.data.model.SpesaConDettagli;
import com.example.paripariapp.data.model.SpesaPartecipante;
import com.example.paripariapp.data.repository.CurrencyRepository;
import com.example.paripariapp.databinding.FragmentDettaglioSpesaBinding;
import com.example.paripariapp.databinding.ItemQuotaPartecipanteBinding;
import com.example.paripariapp.ui.viewmodel.DettaglioSchedaViewModel;
import com.example.paripariapp.util.CategoriaUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DettaglioSpesaFragment extends Fragment {

    private static final String ARG_SPESA_ID = "arg_spesa_id";
    private static final String ARG_SCHEDA_ID = "arg_scheda_id";
    private static final String ARG_VALUTA = "arg_valuta";

    private FragmentDettaglioSpesaBinding binding;
    private DettaglioSchedaViewModel viewModel;

    private String spesaId;
    private String schedaId;
    private String valutaGruppo;

    private List<Partecipante> partecipantiCache = new ArrayList<>();
    private List<SpesaPartecipante> quoteCache = new ArrayList<>();
    private SpesaConDettagli spesaCorrente;

    public static DettaglioSpesaFragment newInstance(String spesaId, String schedaId, String valuta) {
        DettaglioSpesaFragment fragment = new DettaglioSpesaFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SPESA_ID, spesaId);
        args.putString(ARG_SCHEDA_ID, schedaId);
        args.putString(ARG_VALUTA, valuta);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            spesaId = getArguments().getString(ARG_SPESA_ID);
            schedaId = getArguments().getString(ARG_SCHEDA_ID);
            valutaGruppo = getArguments().getString(ARG_VALUTA);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDettaglioSpesaBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DettaglioSchedaViewModel.class);

        binding.toolbarDettaglioSpesa.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });

        binding.toolbarDettaglioSpesa.setOnMenuItemClickListener(item -> {
            if (spesaCorrente == null || spesaCorrente.getSpesa() == null) return false;
            Spesa spesa = spesaCorrente.getSpesa();

            if (CategoriaUtil.isCategoriaSaldi(spesa.getCategoria())) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.badge_saldato_effettuato)
                        .setMessage(R.string.msg_spesa_saldo_non_modificabile)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            }

            // Controlla se qualche partecipante ha lasciato il gruppo
            java.util.Set<String> activeIds = new java.util.HashSet<>();
            if (partecipantiCache != null) {
                for (Partecipante p : partecipantiCache) {
                    activeIds.add(p.getId());
                }
            }

            boolean haMembroAssente = false;
            if (spesa.getPagatoDaId() != null && !activeIds.contains(spesa.getPagatoDaId())) {
                haMembroAssente = true;
            }
            if (!haMembroAssente && quoteCache != null) {
                for (SpesaPartecipante q : quoteCache) {
                    if (q.getSpesaId().equals(spesa.getId())) {
                        if (q.getPartecipanteId() != null && !activeIds.contains(q.getPartecipanteId())) {
                            haMembroAssente = true;
                            break;
                        }
                    }
                }
            }

            if (haMembroAssente) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_titolo_spesa_non_modificabile)
                        .setMessage(R.string.dialog_msg_spesa_membro_assente)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            }

            int id = item.getItemId();
            if (id == R.id.action_modifica_spesa) {
                ModificaSpesaFragment fragment = ModificaSpesaFragment.newInstance(
                        spesa.getId(),
                        schedaId,
                        valutaGruppo
                );
                getParentFragmentManager().beginTransaction()
                        .setCustomAnimations(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        )
                        .replace(R.id.dettaglio_container, fragment)
                        .addToBackStack(null)
                        .commit();
                return true;
            } else if (id == R.id.action_elimina_spesa) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dialog_titolo_elimina_spesa)
                        .setMessage(R.string.dialog_msg_elimina_spesa)
                        .setPositiveButton(R.string.btn_elimina, (dialog, which) -> {
                            viewModel.eliminaSpesa(spesa.getId(), schedaId);
                            Toast.makeText(requireContext(), R.string.msg_spesa_eliminata, Toast.LENGTH_SHORT).show();
                            if (getParentFragmentManager() != null) {
                                getParentFragmentManager().popBackStack();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
            return false;
        });

        setupObservers();
    }

    private void setupObservers() {
        if (schedaId != null) {
            viewModel.getPartecipanti(schedaId).observe(getViewLifecycleOwner(), partecipanti -> {
                partecipantiCache = partecipanti != null ? partecipanti : new ArrayList<>();
                aggiornaInterfaccia();
            });

            viewModel.getQuoteDellaScheda(schedaId).observe(getViewLifecycleOwner(), quote -> {
                quoteCache = quote != null ? quote : new ArrayList<>();
                aggiornaInterfaccia();
            });

            viewModel.getSpeseConDettagli(schedaId).observe(getViewLifecycleOwner(), spese -> {
                if (spese != null) {
                    for (SpesaConDettagli scd : spese) {
                        if (scd.getSpesa() != null && scd.getSpesa().getId().equals(spesaId)) {
                            spesaCorrente = scd;
                            break;
                        }
                    }
                    aggiornaInterfaccia();
                }
            });
        }
    }

    private void aggiornaInterfaccia() {
        if (binding == null || spesaCorrente == null || spesaCorrente.getSpesa() == null) return;

        Spesa spesa = spesaCorrente.getSpesa();
        String valutaSpesa = spesa.getValuta();
        String gruppoVal = valutaGruppo != null ? valutaGruppo : "EUR";

        // Emoji & Categoria
        String categoria = spesa.getCategoria() != null ? spesa.getCategoria() : getString(R.string.cat_altro);
        String emoji = CategoriaUtil.getEmojiForCategoria(categoria, spesa.getTitolo());
        binding.tvCategoriaDettaglio.setText(emoji + " " + categoria);

        // Titolo
        binding.tvTitoloDettaglio.setText(spesa.getTitolo());

        // Data
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault());
        String dataFmt = formatter.format(
                Instant.ofEpochMilli(spesa.getDataSpesa()).atZone(ZoneId.systemDefault())
        );
        binding.tvDataDettaglio.setText(dataFmt);

        // Importo Totale
        binding.tvImportoTotaleDettaglio.setText(
                getString(R.string.spesa_formato_importo, spesa.getImporto(), valutaSpesa)
        );

        // Controllo multivaluta
        if (!valutaSpesa.equalsIgnoreCase(gruppoVal)) {
            CurrencyRepository currencyRepo = CurrencyRepository.getInstance(requireContext().getApplicationContext());
            double rateSpesa = currencyRepo.getRate(valutaSpesa);
            double rateGruppo = currencyRepo.getRate(gruppoVal);
            double importoConvertito = (spesa.getImporto() / rateSpesa) * rateGruppo;

            binding.tvSecondariaValutaDettaglio.setVisibility(View.VISIBLE);
            binding.tvSecondariaValutaDettaglio.setText(
                    getString(R.string.spesa_formato_importo, importoConvertito, gruppoVal)
            );
        } else {
            binding.tvSecondariaValutaDettaglio.setVisibility(View.GONE);
        }

        // Spesa pagata da: + Nome
        String pagatoreNome = spesaCorrente.getNomePagatore() != null ? spesaCorrente.getNomePagatore() : getString(R.string.nome_sconosciuto);
        String testoPagatoDa = "Spesa pagata da: " + pagatoreNome;
        binding.tvPagatoDaDettaglio.setText(testoPagatoDa);

        // Quote partecipanti
        setupQuoteRecycler(spesa, valutaSpesa, gruppoVal);
    }

    private void setupQuoteRecycler(Spesa spesa, String valutaSpesa, String gruppoVal) {
        List<SpesaPartecipante> quoteDellaSpesa = new ArrayList<>();
        if (quoteCache != null) {
            for (SpesaPartecipante q : quoteCache) {
                if (q.getSpesaId().equals(spesa.getId())) {
                    quoteDellaSpesa.add(q);
                }
            }
        }

        QuoteDettaglioAdapter adapter = new QuoteDettaglioAdapter(quoteDellaSpesa, partecipantiCache, valutaSpesa, gruppoVal);
        binding.recyclerQuoteDettaglio.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerQuoteDettaglio.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class QuoteDettaglioAdapter extends RecyclerView.Adapter<QuoteDettaglioAdapter.ViewHolder> {
        private final List<SpesaPartecipante> quote;
        private final List<Partecipante> partecipanti;
        private final String valutaSpesa;
        private final String gruppoVal;

        QuoteDettaglioAdapter(List<SpesaPartecipante> quote, List<Partecipante> partecipanti, String valutaSpesa, String gruppoVal) {
            this.quote = quote;
            this.partecipanti = partecipanti;
            this.valutaSpesa = valutaSpesa;
            this.gruppoVal = gruppoVal;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemQuotaPartecipanteBinding binding = ItemQuotaPartecipanteBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false
            );
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SpesaPartecipante q = quote.get(position);
            String nome = "Membro";
            if (partecipanti != null) {
                for (Partecipante p : partecipanti) {
                    if (p.getId().equals(q.getPartecipanteId())) {
                        nome = p.getNome();
                        break;
                    }
                }
            }

            holder.binding.spuntaPartecipante.setVisibility(View.GONE);
            holder.binding.contenitoreQuota.setVisibility(View.GONE);
            holder.binding.tvQuotaEqua.setVisibility(View.VISIBLE);

            holder.binding.nomePartecipante.setText(nome);

            String importoStr = String.format(Locale.getDefault(), "%.2f %s", q.getQuota(), valutaSpesa);
            if (!valutaSpesa.equalsIgnoreCase(gruppoVal)) {
                CurrencyRepository currencyRepo = CurrencyRepository.getInstance(holder.itemView.getContext().getApplicationContext());
                double rateSpesa = currencyRepo.getRate(valutaSpesa);
                double rateGruppo = currencyRepo.getRate(gruppoVal);
                double importoConv = (q.getQuota() / rateSpesa) * rateGruppo;
                importoStr += String.format(Locale.getDefault(), " (≈ %.2f %s)", importoConv, gruppoVal);
            }
            holder.binding.tvQuotaEqua.setText(importoStr);
        }

        @Override
        public int getItemCount() {
            return quote.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ItemQuotaPartecipanteBinding binding;

            ViewHolder(ItemQuotaPartecipanteBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
