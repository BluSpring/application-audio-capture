use jni::objects::{JClass, JObject, JObjectArray, JString};
use jni::refs::Global;
use jni::sys::{jbyte, jint, jlong, jshort};
use jni::{EnvUnowned, JValue, JavaVM, jni_sig, jni_str};
use pocketstation::connector::Connector;
use pocketstation::{
    EndpointHandle, ProcessId, RunningSession, SampleFormat, SampleSpec, Session, Source,
    SourceKind,
};
use std::ptr::with_exposed_provenance_mut;

fn create_destination(session: &Session, callback: Global<JObject<'static>>) -> EndpointHandle {
    session
        .destination(
            Connector::from_audio_fn(move |frame| {
                let samples = frame.samples();
                if !samples.is_empty() {
                    let vm = JavaVM::singleton().unwrap();
                    vm.attach_current_thread(|env| -> jni::errors::Result<()> {
                        unsafe {
                            let byte_slice = std::slice::from_raw_parts(
                                samples.as_ptr() as *const u8,
                                samples.len() * size_of::<f32>(),
                            );
                            let buffer = env
                                .new_direct_byte_buffer(
                                    byte_slice.as_ptr() as *mut u8,
                                    byte_slice.iter().len(),
                                )
                                .unwrap();

                            env.call_method(
                                &callback,
                                jni_str!("onAudioFrame"),
                                jni_sig!("(Ljava/nio/ByteBuffer;)V"),
                                &[JValue::Object(&buffer)],
                            )
                            .unwrap();
                        }

                        Ok(())
                    })
                    .unwrap();
                }

                Ok(())
            })
            .unwrap(),
        )
        .unwrap()
}

#[unsafe(export_name = "Java_xyz_bluspring_unitytranslate_capture_application_ApplicationAudioCapture_startCaptureId")]
pub extern "system" fn start_capture_application<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    sample_rate: jint,
    channels: jbyte,
    process_id: jint,
    callback: JObject<'local>,
) -> jlong {
    let outcome = unowned_env.with_env(|env| -> jni::errors::Result<jlong> {
        let session = Session::builder()
            .sample_spec(SampleSpec::new(
                sample_rate as u32,
                channels as u8,
                SampleFormat::F32Interleaved,
            ))
            .build();

        let callback = env.new_global_ref(callback)?;

        let application = session
            .capture(Source::application(ProcessId::new(process_id as u32)))
            .unwrap();
        let destination = create_destination(&session, callback);

        application.send(destination).unwrap();
        let running = session.start().unwrap();

        Ok(Box::into_raw(Box::new(running)).expose_provenance() as jlong)
    });

    outcome.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(export_name = "Java_xyz_bluspring_unitytranslate_capture_application_ApplicationAudioCapture_startCaptureName")]
pub extern "system" fn start_capture_application_name<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    sample_rate: jint,
    channels: jbyte,
    process_name: JString<'local>,
    callback: JObject<'local>,
) -> jlong {
    let outcome = unowned_env.with_env(|env| -> jni::errors::Result<jlong> {
        let session = Session::builder()
            .sample_spec(SampleSpec::new(
                sample_rate as u32,
                channels as u8,
                SampleFormat::F32Interleaved,
            ))
            .build();

        let callback = env.new_global_ref(callback)?;

        let process_name = process_name.try_to_string(env).unwrap();
        let application = session.capture(Source::application(process_name)).unwrap();
        let destination = create_destination(&session, callback);

        application.send(destination).unwrap();
        let running = session.start().unwrap();

        Ok(Box::into_raw(Box::new(running)).expose_provenance() as jlong)
    });

    outcome.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(export_name = "Java_xyz_bluspring_unitytranslate_capture_application_ApplicationAudioCapture_startCaptureSystemAudio")]
pub extern "system" fn start_capture_system_audio<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    sample_rate: jint,
    channels: jbyte,
    callback: JObject<'local>,
) -> jlong {
    let outcome = unowned_env.with_env(|env| -> jni::errors::Result<jlong> {
        let session = Session::builder()
            .sample_spec(SampleSpec::new(
                sample_rate as u32,
                channels as u8,
                SampleFormat::F32Interleaved,
            ))
            .build();

        let callback = env.new_global_ref(callback)?;

        let application = session.capture(Source::system_audio()).unwrap();
        let destination = create_destination(&session, callback);

        application.send(destination).unwrap();
        let running = session.start().unwrap();

        Ok(Box::into_raw(Box::new(running)).expose_provenance() as jlong)
    });

    outcome.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(export_name = "Java_xyz_bluspring_unitytranslate_capture_application_ApplicationAudioCapture_listSources")]
pub extern "system" fn list_sources<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
) -> JObjectArray<'local> {
    let outcome = unowned_env.with_env(|env| -> jni::errors::Result<JObjectArray<'local>> {
        let sources: Vec<_> = pocketstation::discover_sources();
        let sources_array = JObjectArray::<JObject>::new(env, sources.len(), JObject::null()).unwrap();

        for i in 0..sources.len() {
            let source = sources.get(i).unwrap();

            let name = JString::new(env, &source.name)?;
            let stable_key = JString::new(env, &source.stable_id.stable_key)?;
            let int_class = env.find_class(jni_str!("java/lang/Integer"))?;

            if source.stable_id.kind == SourceKind::Application {
                let application_class = env.find_class(jni_str!("xyz/bluspring/unitytranslate/capture/application/source/SourceInfo$Application"))?;

                let process_id = &source.process_id;
                let process_id = if process_id.is_some() {
                    env.call_static_method(int_class, jni_str!("valueOf"), jni_sig!("(I)Ljava/lang/Integer;"), &[JValue::Int(process_id.unwrap_or(0) as jint)])?.into_object()?
                } else {
                    JObject::null()
                };

                let application_id = source.app_id.as_ref();
                let application_id = if application_id.is_some() {
                    JString::new(env, &application_id.unwrap())?
                } else {
                    JString::null()
                };

                let data = env.new_object(application_class, jni_sig!("(ISILjava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/String;)V"), &[
                    JValue::Int(source.sample_rate_hz as jint),
                    JValue::Short(source.channels as jshort),
                    JValue::Int(source.state as jint),
                    JValue::Object(&name),
                    JValue::Object(&stable_key),
                    JValue::Object(&process_id),
                    JValue::Object(&application_id),
                ])?;

                sources_array.set_element(env, i, data)?;
            } else {
                let generic_class = env.find_class(jni_str!("xyz/bluspring/unitytranslate/capture/application/source/SourceInfo$Generic"))?;

                let data = env.new_object(generic_class, jni_sig!("(ISIILjava/lang/String;Ljava/lang/String;)V"), &[
                    JValue::Int(source.sample_rate_hz as jint),
                    JValue::Short(source.channels as jshort),
                    JValue::Int(source.stable_id.kind as jint),
                    JValue::Int(source.state as jint),
                    JValue::Object(&name),
                    JValue::Object(&stable_key),
                ])?;

                sources_array.set_element(env, i, data)?;
            }
        }

        Ok(sources_array)
    });

    outcome.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}

#[unsafe(export_name = "Java_xyz_bluspring_unitytranslate_capture_application_ApplicationAudioCapture_stopCapture")]
pub extern "system" fn stop_capture<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    session_ptr: jlong,
) {
    let outcome = unowned_env.with_env(|_env| -> jni::errors::Result<()> {
        let session_ptr: *mut RunningSession = with_exposed_provenance_mut(session_ptr as usize);
        let session = unsafe { Box::leak(Box::from_raw(session_ptr)) };

        session.stop();

        unsafe { drop(Box::from_raw(session_ptr)) }

        Ok(())
    });

    outcome.resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}
