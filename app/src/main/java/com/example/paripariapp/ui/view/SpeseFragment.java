package com.example.paripariapp.ui.view;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.data.repository.PariPariRepository;
import com.example.paripariapp.databinding.FragmentSpeseBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.example.paripariapp.util.AppSnackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment della pagina "Schede Spese".
 * Mostra la lista delle schede spese, l'empty state se vuota,
 * supporta riordinamento (drag & drop), eliminazione fluida con swipe verso sinistra
 * e possibilità di annullare l'eliminazione tramite Snackbar (senza popup bloccanti),
 * accesso tramite codice invito e apertura BottomSheet per nuova scheda.
 */
public class SpeseFragment extends Fragment {

    private FragmentSpeseBinding binding;
    private SpeseViewModel viewModel;
    private SchedaAdapter adapter;

    private Scheda schedaInSospeso = null;
    private Snackbar snackbarElimina = null;

    private final androidx.activity.result.ActivityResultLauncher<String> importCsvLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    eseguiImportazioneCsv(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSpeseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SpeseViewModel.class);

        setupRecyclerView();
        setupObservers();
        setupListeners();
        setupEmptyState();
    }

    private void setupRecyclerView() {
        adapter = new SchedaAdapter(scheda -> {
            confermaEliminazioneInSospeso();
            if (scheda != null) {
                Intent intent = new Intent(requireContext(), DettaglioSchedaActivity.class);
                intent.putExtra(DettaglioSchedaActivity.EXTRA_SCHEDA_ID, scheda.getId());
                intent.putExtra(DettaglioSchedaActivity.EXTRA_TITOLO, scheda.getTitolo());
                intent.putExtra(DettaglioSchedaActivity.EXTRA_VALUTA, scheda.getValutaPredefinita());
                startActivity(intent);
            }
        });

        binding.recyclerSchede.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSchede.setAdapter(adapter);

        setupItemTouchHelper();
    }

    private void setupItemTouchHelper() {
        final ColorDrawable background = new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.delete_red));
        final Drawable deleteIcon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, // Drag: Su e Giù
                ItemTouchHelper.LEFT                      // Swipe: Solo a sinistra
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getBindingAdapterPosition();
                int toPosition = target.getBindingAdapterPosition();

                adapter.moveItem(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position < 0 || position >= adapter.getLocalList().size()) return;
                Scheda schedaSelezionata = adapter.getLocalList().get(position);

                if (direction == ItemTouchHelper.LEFT) {
                    viewModel.verificaSaldiInSospeso(schedaSelezionata.getId(), haSaldi -> {
                        if (!isAdded() || getContext() == null) return;
                        if (haSaldi) {
                            adapter.notifyItemChanged(position);
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.titolo_impossibile_eliminare_scheda)
                                    .setMessage(R.string.msg_errore_eliminazione_scheda_saldi)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        } else {
                            eseguiEliminazioneConUndo(schedaSelezionata);
                        }
                    });
                }
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.setAlpha(0.85f);
                    viewHolder.itemView.setElevation(16f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setAlpha(1.0f);
                viewHolder.itemView.setElevation(0f);

                List<String> orderedIds = new ArrayList<>();
                for (Scheda s : adapter.getLocalList()) {
                    if (s != null && s.getId() != null) {
                        orderedIds.add(s.getId());
                    }
                }
                viewModel.salvaOrdineSchede(orderedIds);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    background.setBounds(
                            itemView.getRight() + (int) dX,
                            itemView.getTop(),
                            itemView.getRight(),
                            itemView.getBottom()
                    );
                    background.draw(c);

                    if (deleteIcon != null) {
                        int itemHeight = itemView.getBottom() - itemView.getTop();
                        int intrinsicWidth = deleteIcon.getIntrinsicWidth();
                        int intrinsicHeight = deleteIcon.getIntrinsicHeight();

                        int iconMargin = (itemHeight - intrinsicHeight) / 2;
                        int iconTop = itemView.getTop() + iconMargin;
                        int iconBottom = iconTop + intrinsicHeight;
                        int iconRight = itemView.getRight() - iconMargin;
                        int iconLeft = iconRight - intrinsicWidth;

                        if (-dX > iconMargin) {
                            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                            deleteIcon.draw(c);
                        }
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        });

        itemTouchHelper.attachToRecyclerView(binding.recyclerSchede);
    }

    private void eseguiEliminazioneConUndo(@NonNull Scheda scheda) {
        confermaEliminazioneInSospeso();

        schedaInSospeso = scheda;

        List<Scheda> listaAggiornata = new ArrayList<>();
        for (Scheda s : adapter.getLocalList()) {
            if (!s.getId().equals(scheda.getId())) {
                listaAggiornata.add(s);
            }
        }

        if (listaAggiornata.isEmpty()) {
            binding.layoutEmptyState.getRoot().setVisibility(View.VISIBLE);
            binding.recyclerSchede.setVisibility(View.GONE);
        }
        adapter.submitList(listaAggiornata);

        snackbarElimina = AppSnackbar.make(
                binding.getRoot(),
                getString(R.string.msg_scheda_eliminata),
                Snackbar.LENGTH_LONG
        );

        snackbarElimina.setAction(R.string.btn_annulla, v -> {
            schedaInSospeso = null;
            if (viewModel.getUiState().getValue() != null) {
                renderUiState(viewModel.getUiState().getValue());
            }
        });

        snackbarElimina.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    confermaEliminazioneInSospeso();
                }
            }
        });

        snackbarElimina.show();
    }

    private void confermaEliminazioneInSospeso() {
        if (schedaInSospeso != null) {
            Scheda daEliminare = schedaInSospeso;
            schedaInSospeso = null;
            viewModel.eliminaScheda(daEliminare.getId());
        }
    }

    private void setupEmptyState() {
        binding.layoutEmptyState.tvEmptyTitle.setText(R.string.empty_schede_titolo);
        binding.layoutEmptyState.tvEmptyDesc.setText(R.string.empty_schede_desc);
    }

    private void setupObservers() {
        // Osserva l'UiState unificato conforme alle linee guida di Android Architecture
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderUiState);
    }

    private void renderUiState(@Nullable com.example.paripariapp.ui.viewmodel.SpeseUiState state) {
        if (state == null) return;

        List<Scheda> schedeDaMostrare = new ArrayList<>();
        if (state.getSchede() != null) {
            for (Scheda s : state.getSchede()) {
                if (schedaInSospeso == null || !s.getId().equals(schedaInSospeso.getId())) {
                    schedeDaMostrare.add(s);
                }
            }
        }

        if (schedeDaMostrare.isEmpty()) {
            binding.layoutEmptyState.getRoot().setVisibility(View.VISIBLE);
            binding.recyclerSchede.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.getRoot().setVisibility(View.GONE);
            binding.recyclerSchede.setVisibility(View.VISIBLE);
            adapter.submitList(schedeDaMostrare);
        }

        if (!state.getConteggioPartecipanti().isEmpty()) {
            adapter.aggiornaConteggioPartecipanti(state.getConteggioPartecipanti());
        }

        if (state.getErrorMessage() != null) {
            AppSnackbar.show(binding.getRoot(), state.getErrorMessage());
        }
    }

    private void setupListeners() {
        binding.fabNuovaScheda.setOnClickListener(v -> {
            confermaEliminazioneInSospeso();
            mostraDialogSceltaNuovaScheda();
        });
    }

    private void mostraDialogSceltaNuovaScheda() {
        String[] opzioni = new String[] {
                getString(R.string.dialog_opzioni_nuova_scheda),
                getString(R.string.dialog_opzioni_entra_scheda),
                getString(R.string.dialog_opzioni_importa_csv)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_opzioni_scheda_titolo)
                .setItems(opzioni, (dialog, which) -> {
                    if (which == 0) {
                        NuovaSchedaBottomSheet.newInstance().show(getChildFragmentManager(), "NuovaSchedaBottomSheet");
                    } else if (which == 1) {
                        mostraDialogCodiceAccesso();
                    } else if (which == 2) {
                        importCsvLauncher.launch("*/*");
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private void eseguiImportazioneCsv(android.net.Uri uri) {
        if (getContext() == null || binding == null) return;
        AppSnackbar.show(binding.getRoot(), "Importazione file CSV in corso...");

        viewModel.importaSchedaDaCsv(requireContext().getApplicationContext(), uri, new PariPariRepository.OnImportCsvCallback() {
            @Override
            public void onSuccess(String schedaId, String titolo) {
                if (!isAdded() || binding == null) return;
                AppSnackbar.show(binding.getRoot(), getString(R.string.msg_importazione_csv_successo, titolo));
                DettaglioSchedaActivity.avvia(requireContext(), schedaId, titolo);
            }

            @Override
            public void onError(String errore) {
                if (!isAdded() || binding == null) return;
                AppSnackbar.showLong(binding.getRoot(), errore);
            }
        });
    }

    private void mostraDialogCodiceAccesso() {
        UniscitiSchedaBottomSheet.newInstance().show(getChildFragmentManager(), "UniscitiSchedaBottomSheet");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (snackbarElimina != null && snackbarElimina.isShown()) {
            snackbarElimina.dismiss();
        }
        confermaEliminazioneInSospeso();
        binding = null;
    }
}