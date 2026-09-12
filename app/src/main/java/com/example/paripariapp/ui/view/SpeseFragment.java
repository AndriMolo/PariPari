package com.example.paripariapp.ui.view;

import android.content.Intent;
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
        // 1. Osserva la lista delle schede
        viewModel.getSchede().observe(getViewLifecycleOwner(), schede -> {
            if (schede == null || schede.isEmpty()) {
                binding.layoutEmptyState.getRoot().setVisibility(View.VISIBLE);
                binding.recyclerSchede.setVisibility(View.GONE);
                binding.tvTitoloGruppi.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyState.getRoot().setVisibility(View.GONE);
                binding.recyclerSchede.setVisibility(View.VISIBLE);
                binding.tvTitoloGruppi.setVisibility(View.VISIBLE);
                adapter.submitList(schede);
            }
        });

        // 2. Osserva tutti i conteggi in una volta sola (nessun ciclo for, caricamento immediato)
        viewModel.getMappaConteggioPartecipanti().observe(getViewLifecycleOwner(), mappaConteggi -> {
            if (mappaConteggi != null) {
                adapter.aggiornaConteggioPartecipanti(mappaConteggi);
            }
        });
    }

    private void setupListeners() {
        binding.fabNuovaScheda.setOnClickListener(v ->
                NuovaSchedaBottomSheet.newInstance().show(getChildFragmentManager(), "NuovaSchedaBottomSheet")
        );

        binding.btnEntraConCodice.setOnClickListener(v -> mostraDialogCodiceAccesso());
    }

    private void mostraDialogCodiceAccesso() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_entra_codice, binding.getRoot(), false);
        dialog.setContentView(sheetView);

        TextInputEditText etCodice = sheetView.findViewById(R.id.etCodice);
        sheetView.findViewById(R.id.btnAnnulla).setOnClickListener(v -> dialog.dismiss());
        sheetView.findViewById(R.id.btnConfermaPartecipa).setOnClickListener(v -> {
            String codice = etCodice.getText() != null ? etCodice.getText().toString().trim() : "";
            if (!codice.isEmpty()) {
                dialog.dismiss();
                // TODO: unisciti al gruppo tramite codice
            } else {
                etCodice.setError(getString(R.string.error_codice_non_valido));
            }
        });

        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}