package com.kickstarter.viewmodels;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

import com.kickstarter.KSApplication;
import com.kickstarter.libs.CurrentUser;
import com.kickstarter.libs.ViewModel;
import com.kickstarter.libs.rx.transformers.Transformers;
import com.kickstarter.libs.utils.I18nUtils;
import com.kickstarter.libs.utils.ListUtils;
import com.kickstarter.models.User;
import com.kickstarter.services.ApiClient;
import com.kickstarter.ui.activities.SettingsActivity;
import com.kickstarter.viewmodels.errors.SettingsViewModelErrors;
import com.kickstarter.viewmodels.inputs.SettingsViewModelInputs;
import com.kickstarter.viewmodels.outputs.SettingsViewModelOutputs;

import javax.inject.Inject;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

public class SettingsViewModel extends ViewModel<SettingsActivity> implements SettingsViewModelInputs,
  SettingsViewModelErrors, SettingsViewModelOutputs {

  // INPUTS
  private final PublishSubject<Void> contactEmailClicked = PublishSubject.create();
  private final PublishSubject<Pair<Boolean, String>> newsletterInput = PublishSubject.create();
  private final PublishSubject<User> userInput = PublishSubject.create();

  // OUTPUTS
  private final PublishSubject<String> sendNewsletterConfirmation = PublishSubject.create();
  public Observable<String> sendNewsletterConfirmation() {
    return sendNewsletterConfirmation;
  }
  private final PublishSubject<Void> updateSuccess = PublishSubject.create();
  public final Observable<Void> updateSuccess() {
    return updateSuccess;
  }
  private final BehaviorSubject<User> userOutput = BehaviorSubject.create();
  public Observable<User> user() {
    return userOutput;
  }

  // ERRORS
  private final PublishSubject<Throwable> unableToSavePreferenceError = PublishSubject.create();
  public final Observable<String> unableToSavePreferenceError() {
    return unableToSavePreferenceError
      .takeUntil(updateSuccess)
      .map(__ -> null);
  }

  public final SettingsViewModelInputs inputs = this;
  public final SettingsViewModelOutputs outputs = this;
  public final SettingsViewModelErrors errors = this;

  @Inject ApiClient client;
  @Inject CurrentUser currentUser;

  @Override
  public void contactEmailClicked() {
    this.contactEmailClicked.onNext(null);
  }

  @Override
  public void notifyMobileOfFollower(final boolean b) {
    userInput.onNext(userOutput.getValue().toBuilder().notifyMobileOfFollower(b).build());
  }

  @Override
  public void notifyMobileOfFriendActivity(final boolean b) {
    userInput.onNext(userOutput.getValue().toBuilder().notifyMobileOfFriendActivity(b).build());
  }

  @Override
  public void notifyMobileOfUpdates(final boolean b) {
    userInput.onNext(userOutput.getValue().toBuilder().notifyMobileOfUpdates(b).build());
  }

  @Override
  public void notifyOfFollower(final boolean b) {
    userInput.onNext(userOutput.getValue().toBuilder().notifyOfFollower(b).build());
  }

  @Override
  public void notifyOfFriendActivity(final boolean b) {
    userInput.onNext(userOutput.getValue().toBuilder().notifyOfFriendActivity(b).build());
  }

  @Override
  public void notifyOfUpdates(final boolean b) {
    userInput.onNext(userOutput.getValue().toBuilder().notifyOfUpdates(b).build());
  }

  @Override
  public void sendHappeningNewsletter(final boolean checked, final @NonNull String name) {
    userInput.onNext(userOutput.getValue().toBuilder().happeningNewsletter(checked).build());
    newsletterInput.onNext(new Pair<>(checked, name));
  }

  @Override
  public void sendPromoNewsletter(final boolean checked, final @NonNull String name) {
    userInput.onNext(userOutput.getValue().toBuilder().promoNewsletter(checked).build());
    newsletterInput.onNext(new Pair<>(checked, name));
  }

  @Override
  public void sendWeeklyNewsletter(final boolean checked, final @NonNull String name) {
    userInput.onNext(userOutput.getValue().toBuilder().weeklyNewsletter(checked).build());
    newsletterInput.onNext(new Pair<>(checked, name));
  }

  @Override
  protected void onCreate(final @NonNull Context context, final @Nullable Bundle savedInstanceState) {
    super.onCreate(context, savedInstanceState);
    ((KSApplication) context.getApplicationContext()).component().inject(this);

    addSubscription(
      client.fetchCurrentUser()
        .retry(2)
        .onErrorResumeNext(e -> Observable.empty())
        .subscribe(currentUser::refresh)
    );

    addSubscription(
      currentUser.observable()
        .take(1)
        .subscribe(userOutput::onNext)
    );

    addSubscription(
      userInput
        .concatMap(this::updateSettings)
        .subscribe(this::success)
    );

    addSubscription(
      userInput
        .subscribe(userOutput)
    );

    addSubscription(
      userOutput
        .window(2, 1)
        .flatMap(Observable::toList)
        .map(ListUtils::first)
        .compose(Transformers.takeWhen(unableToSavePreferenceError))
        .subscribe(userOutput)
    );

    addSubscription(
      currentUser.observable()
        .compose(Transformers.takePairWhen(newsletterInput))
        .filter(us -> requiresDoubleOptIn(us.first, us.second.first))
        .map(us -> us.second.second)
        .subscribe(sendNewsletterConfirmation)
    );

    addSubscription(
      contactEmailClicked.subscribe(__ -> koala.trackContactEmailClicked())
    );

    addSubscription(
      newsletterInput
        .map(bs -> bs.first)
        .subscribe(koala::trackNewsletterToggle)
    );

    koala.trackSettingsView();
  }

  private boolean requiresDoubleOptIn(final @NonNull User user, final boolean checked) {
    return I18nUtils.isCountryGermany(user.location()) && checked;
  }

  private void success(final @NonNull User user) {
    currentUser.refresh(user);
    this.updateSuccess.onNext(null);
  }

  private Observable<User> updateSettings(final @NonNull User user) {
    return client.updateUserSettings(user)
      .compose(Transformers.pipeErrorsTo(unableToSavePreferenceError));
  }
}
