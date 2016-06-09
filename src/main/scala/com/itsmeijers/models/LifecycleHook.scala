package com.itsmeijers.models

case class LifecycleHook(
  name: String,
  groupName: String,
  transitionType: String, // INSTANCE_LAUNCH \/ INSTANCE_LAUNCH_ERROR \/ INSTANCE_TERMINATE \/ INSTANCE_TERMINATE_ERROR
  token: String,
  instanceId: String,
  metaData: String)
