package A1BASES;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class A7_SharedViewModel extends ViewModel {
    private MutableLiveData<Boolean> clearRecyclerView = new MutableLiveData<>();

    public void triggerClear() {
        clearRecyclerView.setValue(true);
        clearRecyclerView.setValue(false); // Resetear para futuras llamadas
    }

    public LiveData<Boolean> getClearRecyclerView() {
        return clearRecyclerView;
    }
}