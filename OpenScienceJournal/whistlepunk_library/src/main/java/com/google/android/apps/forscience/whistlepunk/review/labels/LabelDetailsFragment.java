/*
 *  Copyright 2017 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk.review.labels;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.apps.forscience.whistlepunk.AppSingleton;
import com.google.android.apps.forscience.whistlepunk.DataController;
import com.google.android.apps.forscience.whistlepunk.DataService;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.RxDataController;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.filemetadata.Label;
import com.google.android.apps.forscience.whistlepunk.project.experiment.ExperimentDetailsFragment;

import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;

/**
 * General fragment for label details views
 */
abstract class LabelDetailsFragment extends Fragment {
    private static final String KEY_SAVED_LABEL = "saved_label";
    protected String mExperimentId;
    protected BehaviorSubject<Experiment> mExperiment = BehaviorSubject.create();
    protected Label mOriginalLabel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExperimentId = getArguments().getString(LabelDetailsActivity.ARG_EXPERIMENT_ID);
        if (savedInstanceState == null) {
            mOriginalLabel = getArguments().getParcelable(LabelDetailsActivity.ARG_LABEL);
        } else {
            // Load the updated label
            mOriginalLabel = savedInstanceState.getParcelable(KEY_SAVED_LABEL);
        }

        // Get the data controller and ask it to get the experiment and subscribe to that event.
        getDataController()
                .flatMap(dc -> RxDataController.getExperimentById(dc, mExperimentId))
                .subscribe(this::attachExperiment);
        mExperiment.firstElement().subscribe(experiment -> getActivity().invalidateOptionsMenu());

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_SAVED_LABEL, mOriginalLabel);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_delete);
        // Disable delete until the experiment is loaded.
        item.setEnabled(mExperiment.hasValue());
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            boolean labelDeleted = false;
            returnToParent(labelDeleted);
            return true;
        } else if (id == R.id.action_delete) {
            deleteAndReturnToParent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void saveUpdatedOriginalLabel() {
        mExperiment.firstElement().subscribe(experiment -> {
            experiment.updateLabel(mOriginalLabel);
            getDataController().subscribe(
                    dc -> RxDataController.updateExperiment(dc, experiment));
        });
    }

    private void attachExperiment(Experiment experiment) {
        mExperiment.onNext(experiment);
    }

    protected Single<DataController> getDataController() {
        return DataService.bind(getActivity()).map(AppSingleton::getDataController);
    }

    protected void returnToParent(boolean labelDeleted) {
        if (getActivity() == null) {
            return;
        }
        if (!mExperiment.hasValue()) {
            // We didn't load yet, just go back.
            getActivity().onBackPressed();
        }
        // Need to either fake a back button or send the right args
        Intent upIntent = NavUtils.getParentActivityIntent(getActivity());
        upIntent.putExtra(ExperimentDetailsFragment.ARG_EXPERIMENT_ID, mExperimentId);
        if (labelDeleted) {
            // Add info to the intent so that the deleted label can have a snackbar undo action.
            upIntent.putExtra(ExperimentDetailsFragment.ARG_DELETED_LABEL, mOriginalLabel);
        }
        // TODO: In the Panes world, do we still need to pass any other args?

        // TODO: nice transition!

        NavUtils.navigateUpTo(getActivity(), upIntent);
    }

    protected void deleteAndReturnToParent() {
        mExperiment.firstElement().subscribe(experiment -> {
            experiment.deleteLabel(mOriginalLabel, getActivity());
            getDataController().subscribe(dc -> RxDataController.updateExperiment(dc, experiment)
                    .subscribe(() -> returnToParent(/* label deleted */ true)));
                });
    }
}
