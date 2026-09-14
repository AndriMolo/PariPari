package com.example.paripariapp.ui.view;

import android.content.Intent;
import android.widget.Toast;
import com.example.paripariapp.data.repository.PariPariRepository;
import android.graphics.Canvas;
import android.graphics.Color;
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
import com.example.paripariapp.databinding.FragmentSpeseBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

/**
 * Fragment della pagina "Schede Spese".
 * Mostra la lista delle schede spese, l'empty state se vuota,
 * supporta riordinamento (drag & drop), eliminazione con swipe verso sinistra,
 * accesso tramite codice invito e apertura BottomSheet per nuova scheda.
 */
public class SpeseFragment extends Fragment {

    private FragmentSpeseBinding binding;
    private SpeseViewModel viewModel;
    private SchedaAdapter adapter;

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
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.dialog_titolo_elimina_scheda)
                                    .setMessage(R.string.dialog_msg_elimina_scheda)
                                    .setPositiveButton(R.string.btn_elimina, (dialog, which) -> {
                                        viewModel.eliminaScheda(schedaSelezionata);
                                    })
                                    .setNegativeButton(R.string.btn_annulla, (dialog, which) -> {
                                        adapter.notifyItemChanged(position);
                                    })
                                    .setOnCancelListener(dialog -> adapter.notifyItemChanged(position))
                                    .show();
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

        if (state.isEmpty()) {
            binding.layoutEmptyState.getRoot().setVisibility(View.VISIBLE);
            binding.recyclerSchede.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.getRoot().setVisibility(View.GONE);
            binding.recyclerSchede.setVisibility(View.VISIBLE);
            adapter.submitList(state.getSchede());
        }

        if (!state.getConteggioPartecipanti().isEmpty()) {
            adapter.aggiornaConteggioPartecipanti(state.getConteggioPartecipanti());
        }

        if (state.getErrorMessage() != null) {
            Snackbar.make(binding.getRoot(), state.getErrorMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        binding.fabNuovaScheda.setOnClickListener(v -> mostraDialogSceltaNuovaScheda());

    }

    private void mostraDialogSceltaNuovaScheda() {
        String[] opzioni = new String[] {
                getString(R.string.dialog_opzioni_nuova_scheda),
                getString(R.string.dialog_opzioni_entra_scheda)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_opzioni_scheda_titolo)
                .setItems(opzioni, (dialog, which) -> {
                    if (which == 0) {
                        NuovaSchedaBottomSheet.newInstance().show(getChildFragmentManager(), "NuovaSchedaBottomSheet");
                    } else if (which == 1) {
                        mostraDialogCodiceAccesso();
                    }
                })
                .setNegativeButton(R.string.btn_annulla, null)
                .show();
    }

    private void mostraDialogCodiceAccesso() {
        UniscitiSchedaBottomSheet.newInstance().show(getChildFragmentManager(), "UniscitiSchedaBottomSheet");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}