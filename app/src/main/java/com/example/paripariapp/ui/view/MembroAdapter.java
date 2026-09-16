package com.example.paripariapp.ui.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paripariapp.R;
import com.example.paripariapp.data.model.Partecipante;
import com.example.paripariapp.databinding.ItemMembroGestioneBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapter per la gestione dei partecipanti di una scheda.
 * Supporta la modifica inline del nome del membro senza aprire schede esterne o finestre di dialogo.
 */
public class MembroAdapter extends ListAdapter<Partecipante, MembroAdapter.MembroViewHolder> {

    private static final DiffUtil.ItemCallback<Partecipante> DIFF_CALLBACK = new DiffUtil.ItemCallback<Partecipante>() {
        @Override
        public boolean areItemsTheSame(@NonNull Partecipante oldItem, @NonNull Partecipante newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Partecipante oldItem, @NonNull Partecipante newItem) {
            return Objects.equals(oldItem.getNome(), newItem.getNome()) &&
                    Objects.equals(oldItem.getEmail(), newItem.getEmail()) &&
                    Objects.equals(oldItem.getStato(), newItem.getStato()) &&
                    Objects.equals(oldItem.getSchedaId(), newItem.getSchedaId());
        }
    };

    private OnEliminaClickListener onEliminaClickListener;
    private OnRiattivaClickListener onRiattivaClickListener;
    private OnEliminaDefinitivamenteClickListener onEliminaDefinitivamenteClickListener;
    private OnNomeModificatoListener onNomeModificatoListener;
    private boolean isCapogruppo = false;
    private FirebaseUser currentUser;
    private String currentMyId;
    private String editingParticipantId = null;

    public interface OnEliminaClickListener {
        void onEliminaClick(Partecipante partecipante);
    }

    public interface OnRiattivaClickListener {
        void onRiattivaClick(Partecipante partecipante);
    }

    public interface OnEliminaDefinitivamenteClickListener {
        void onEliminaDefinitivamenteClick(Partecipante partecipante);
    }

    public interface OnNomeModificatoListener {
        void onNomeModificato(Partecipante partecipante, String nuovoNome);
    }

    public MembroAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnEliminaClickListener(OnEliminaClickListener listener) {
        this.onEliminaClickListener = listener;
    }

    public void setOnRiattivaClickListener(OnRiattivaClickListener listener) {
        this.onRiattivaClickListener = listener;
    }

    public void setOnEliminaDefinitivamenteClickListener(OnEliminaDefinitivamenteClickListener listener) {
        this.onEliminaDefinitivamenteClickListener = listener;
    }

    public void setOnNomeModificatoListener(OnNomeModificatoListener listener) {
        this.onNomeModificatoListener = listener;
    }

    private String schedaId;

    public void setSchedaId(String schedaId) {
        this.schedaId = schedaId;
    }

    public void setCurrentMyId(String currentMyId) {
        this.currentMyId = currentMyId;
    }

    public void setCapogruppo(boolean capogruppo) {
        if (this.isCapogruppo != capogruppo) {
            this.isCapogruppo = capogruppo;
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    @Override
    public void submitList(@Nullable List<Partecipante> list) {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentMyId == null && list != null && !list.isEmpty()) {
            currentMyId = Partecipante.findCurrentUserId(list, currentUser);
        }
        if (editingParticipantId != null && list != null) {
            boolean found = false;
            for (Partecipante p : list) {
                if (Objects.equals(p.getId(), editingParticipantId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                editingParticipantId = null;
            }
        }
        super.submitList(list != null ? new ArrayList<>(list) : null);
    }

    @NonNull
    @Override
    public MembroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMembroGestioneBinding binding = ItemMembroGestioneBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new MembroViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MembroViewHolder holder, int position) {
        Partecipante p = getItem(position);

        Context context = holder.binding.getRoot().getContext();
        com.example.paripariapp.data.repository.UserPreferencesRepository prefs =
                com.example.paripariapp.data.repository.UserPreferencesRepository.getInstance(context);
        String myId = (currentMyId != null) ? currentMyId : (schedaId != null ? prefs.getMyParticipantId(schedaId) : null);

        boolean isMe = (myId != null && myId.equals(p.getId())) ||
                Partecipante.isCurrentUserParticipant(p, currentUser);

        String photoUrl = p.getPhotoUrl();
        if (photoUrl != null && (photoUrl.startsWith("http://") || photoUrl.startsWith("https://"))) {
            holder.binding.avatarIniziale.setVisibility(View.GONE);
            holder.binding.ivAvatarFoto.setVisibility(View.VISIBLE);
            com.example.paripariapp.util.ImageLoaderUtil.caricaImmagine(photoUrl, holder.binding.ivAvatarFoto, R.drawable.ic_account);
        } else {
            holder.binding.ivAvatarFoto.setVisibility(View.GONE);
            holder.binding.avatarIniziale.setVisibility(View.VISIBLE);
            String iniziale = !p.getNome().isEmpty() ? String.valueOf(p.getNome().charAt(0)).toUpperCase(java.util.Locale.getDefault()) : "?";
            holder.binding.avatarIniziale.setText(iniziale);
        }

        boolean isEditing = Objects.equals(p.getId(), editingParticipantId);

        if (isEditing) {
            // Modalità modifica inline
            holder.binding.nomePartecipante.setVisibility(View.GONE);
            holder.binding.bottoneModifica.setVisibility(View.GONE);
            holder.binding.bottoneElimina.setVisibility(View.GONE);

            holder.binding.tilModificaInline.setVisibility(View.VISIBLE);
            holder.binding.tilModificaInline.setError(null);
            holder.binding.bottoneSalvaInline.setVisibility(View.VISIBLE);
            holder.binding.bottoneAnnullaInline.setVisibility(View.VISIBLE);

            String nomeClean = Partecipante.pulisciNome(p.getNome());
            holder.binding.etModificaInline.setText(nomeClean);
            holder.binding.etModificaInline.setSelection(nomeClean.length());
            holder.binding.etModificaInline.requestFocus();
            holder.binding.etModificaInline.post(() -> showKeyboard(holder.binding.etModificaInline));

            Runnable salvaAzione = () -> {
                String rawNome = holder.binding.etModificaInline.getText() != null
                        ? holder.binding.etModificaInline.getText().toString().trim()
                        : "";
                String nuovoNome = Partecipante.pulisciNome(rawNome);
                if (nuovoNome.isEmpty()) {
                    holder.binding.tilModificaInline.setError(
                            holder.binding.getRoot().getContext().getString(R.string.error_nome_obbligatorio));
                    return;
                }
                holder.binding.tilModificaInline.setError(null);
                hideKeyboard(holder.binding.etModificaInline);
                editingParticipantId = null;
                notifyItemRangeChanged(0, getItemCount());

                if (onNomeModificatoListener != null && !nuovoNome.equals(p.getNome())) {
                    onNomeModificatoListener.onNomeModificato(p, nuovoNome);
                }
            };

            holder.binding.bottoneSalvaInline.setOnClickListener(v -> salvaAzione.run());

            holder.binding.etModificaInline.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    salvaAzione.run();
                    return true;
                }
                return false;
            });

            holder.binding.bottoneAnnullaInline.setOnClickListener(v -> {
                hideKeyboard(holder.binding.etModificaInline);
                editingParticipantId = null;
                notifyItemRangeChanged(0, getItemCount());
            });

        } else {
            // Modalità visualizzazione normale
            holder.binding.tilModificaInline.setVisibility(View.GONE);
            holder.binding.tilModificaInline.setError(null);
            holder.binding.bottoneSalvaInline.setVisibility(View.GONE);
            holder.binding.bottoneAnnullaInline.setVisibility(View.GONE);

            boolean isExMembro = !p.isAttivo();

            if (isExMembro) {
                holder.binding.getRoot().setAlpha(0.5f);
                String nomeClean = Partecipante.pulisciNome(p.getNome());
                String nomeDisplay = nomeClean + " (" + context.getString(R.string.stato_ex_membro) + ")";
                holder.binding.nomePartecipante.setText(nomeDisplay);
                holder.binding.nomePartecipante.setVisibility(View.VISIBLE);

                holder.binding.bottoneModifica.setVisibility(View.VISIBLE);
                holder.binding.bottoneModifica.setIconResource(R.drawable.ic_add);
                holder.binding.bottoneModifica.setContentDescription(context.getString(R.string.btn_riattiva_membro));
                holder.binding.bottoneModifica.setOnClickListener(v -> {
                    if (onRiattivaClickListener != null) {
                        onRiattivaClickListener.onRiattivaClick(p);
                    }
                });

                if (isCapogruppo) {
                    holder.binding.bottoneElimina.setVisibility(View.VISIBLE);
                    holder.binding.bottoneElimina.setIconResource(R.drawable.ic_delete);
                    holder.binding.bottoneElimina.setContentDescription(context.getString(R.string.btn_elimina_definitivamente));
                    holder.binding.bottoneElimina.setOnClickListener(v -> {
                        if (onEliminaDefinitivamenteClickListener != null) {
                            onEliminaDefinitivamenteClickListener.onEliminaDefinitivamenteClick(p);
                        }
                    });
                } else {
                    holder.binding.bottoneElimina.setVisibility(View.GONE);
                }
            } else {
                holder.binding.getRoot().setAlpha(1.0f);
                String nomeDisplay = Partecipante.formattaNomePerVisualizzazione(context, p, isMe);
                holder.binding.nomePartecipante.setText(nomeDisplay);
                holder.binding.nomePartecipante.setVisibility(View.VISIBLE);

                boolean canEdit = isMe || isCapogruppo;
                holder.binding.bottoneModifica.setVisibility(canEdit ? View.VISIBLE : View.GONE);
                holder.binding.bottoneModifica.setIconResource(R.drawable.ic_edit);
                holder.binding.bottoneModifica.setContentDescription(context.getString(R.string.dettaglio_rinomina_partecipante));
                holder.binding.bottoneModifica.setOnClickListener(v -> {
                    editingParticipantId = p.getId();
                    notifyItemRangeChanged(0, getItemCount());
                });

                holder.binding.bottoneElimina.setVisibility((isCapogruppo || isMe) ? View.VISIBLE : View.GONE);
                holder.binding.bottoneElimina.setIconResource(R.drawable.ic_delete);
                holder.binding.bottoneElimina.setContentDescription(context.getString(R.string.btn_rimuovi));
                holder.binding.bottoneElimina.setOnClickListener(v -> {
                    if (onEliminaClickListener != null) {
                        onEliminaClickListener.onEliminaClick(p);
                    }
                });
            }
        }
    }

    private void showKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    private void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    static class MembroViewHolder extends RecyclerView.ViewHolder {
        final ItemMembroGestioneBinding binding;

        MembroViewHolder(@NonNull ItemMembroGestioneBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
