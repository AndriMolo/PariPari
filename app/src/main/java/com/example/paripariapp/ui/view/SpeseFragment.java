package com.example.paripariapp.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Scheda;
import com.example.paripariapp.databinding.FragmentSpeseBinding;
import com.example.paripariapp.ui.viewmodel.SpeseViewModel;
import com.google.android.material.snackbar.Snackbar;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;

/**
 * Fragment della pagina "Schede Spese".
 * Mostra la lista delle schede spese, l'empty state se vuota,
 * supporta riordinamento (drag & drop), eliminazione con swipe verso sinistra
 * e apre il BottomSheet per la creazione rapida di una nuova scheda.
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
            if (scheda != null && getParentFragmentManager() != null) {
                getParentFragmentManager().beginTransaction()
                        .setCustomAnimations(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        )
                        .replace(R.id.fragment_container, DettaglioSchedaFragment.newInstance(
                                scheda.getId(),
                                scheda.getTitolo(),
                                scheda.getValutaPredefinita()
                        ))
                        .addToBackStack("dettaglio_scheda")
                        .commit();
            }
        });

        binding.recyclerSchede.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerSchede.setAdapter(adapter);

        // Configurazione Drag & Drop e Swipe
        setupItemTouchHelper();
    }

    private void setupItemTouchHelper() {
        final ColorDrawable background = new ColorDrawable(Color.parseColor("#E53935")); // Rosso eliminazione
        final Drawable deleteIcon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, // Drag: Su e Giù
                ItemTouchHelper.LEFT                      // Swipe: Solo verso sinistra
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
                Scheda schedaSelezionata = adapter.getCurrentList().get(position);

                if (direction == ItemTouchHelper.LEFT) {
                    viewModel.eliminaScheda(schedaSelezionata);

                    Snackbar.make(binding.recyclerSchede, "Gruppo eliminato", Snackbar.LENGTH_LONG)
                            .setAction("Annulla", v -> viewModel.ripristinaScheda(schedaSelezionata))
                            .show();
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;

                // Se l'utente sta facendo lo swipe a sinistra (dX < 0)
                if (dX < 0) {
                    // 1. Disegna il rettangolo rosso
                    background.setBounds(
                            itemView.getRight() + (int) dX,
                            itemView.getTop(),
                            itemView.getRight(),
                            itemView.getBottom()
                    );
                    background.draw(c);

                    // 2. Disegna l'icona del cestino centrata in altezza
                    if (deleteIcon != null) {
                        int itemHeight = itemView.getBottom() - itemView.getTop();
                        int intrinsicWidth = deleteIcon.getIntrinsicWidth();
                        int intrinsicHeight = deleteIcon.getIntrinsicHeight();

                        int iconMargin = (itemHeight - intrinsicHeight) / 2;
                        int iconTop = itemView.getTop() + iconMargin;
                        int iconBottom = iconTop + intrinsicHeight;
                        int iconRight = itemView.getRight() - iconMargin;
                        int iconLeft = iconRight - intrinsicWidth;

                        // Mostra l'icona solo se c'è abbastanza spazio
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
    }

    private void setupListeners() {
        binding.fabNuovaScheda.setOnClickListener(v -> {
            NuovaSchedaBottomSheet.newInstance().show(getChildFragmentManager(), "NuovaSchedaBottomSheet");
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Evita memory leak
    }
}