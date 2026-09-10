use avian3d::{
    math::AdjustPrecision,
    prelude::{
        Collider, MoveAndSlide, MoveAndSlideConfig, MoveAndSlideHitResponse, PhysicsPlugins,
        RigidBody, SpatialQueryFilter,
    },
};
use bevy::input::mouse::AccumulatedMouseMotion;
use bevy::prelude::*;
use bevy::window::{CursorGrabMode, CursorOptions, PrimaryWindow};
use bevy::asset::UntypedAssetLoadFailedEvent;
use bevy::core_pipeline::tonemapping::Tonemapping;
use std::f32::consts::FRAC_PI_2;

mod lightmap;

const PLAYER_HEIGHT: f32 = 1.5;
const PLAYER_RADIUS: f32 = 0.35;
const PLAYER_CAPSULE_LENGTH: f32 = 1.2;
const MOVE_SPEED: f32 = 4.0;
const MOUSE_SENSITIVITY: f32 = 0.002;
const WALL_HALF_EXTENT: f32 = 8.95;
const WALL_HEIGHT: f32 = 4.95;
const WALL_THICKNESS: f32 = 0.35;

#[derive(Component)]
struct Player;

#[derive(Component)]
struct LookAngles {
    yaw: f32,
    pitch: f32,
}

fn main() {
    App::new()
        .add_plugins(DefaultPlugins.set(WindowPlugin {
            primary_window: Some(Window {
                title: "Backrooms".to_string(),
                resolution: (1280, 720).into(),
                ..default()
            }),
            ..default()
        }))
        .add_plugins(PhysicsPlugins::default())
        .add_systems(Startup, setup)
        .add_systems(Update, (log_asset_failures, grab_cursor, player_look, player_move))
        .run();
}

fn setup(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<StandardMaterial>>,
) {
    commands.spawn(WorldAssetRoot(
        asset_server.load(GltfAssetLabel::Scene(0).from_asset("backrooms/Backroosm.gltf")),
    ));

    commands.spawn((
        AudioPlayer(asset_server.load::<AudioSource>("backrooms/audio.mp3")),
        PlaybackSettings::LOOP,
    ));

    commands.insert_resource(GlobalAmbientLight {
        color: Color::srgb(1.0, 0.92, 0.72),
        brightness: 650.0,
        ..default()
    });

    commands.spawn((
        DirectionalLight {
            illuminance: 2_500.0,
            shadow_maps_enabled: false,
            ..default()
        },
        Transform::from_rotation(Quat::from_euler(EulerRot::XYZ, -1.0, -0.6, 0.0)),
    ));

    spawn_wall_colliders(&mut commands);
    spawn_occlusion_box(&mut commands, &mut meshes, &mut materials);

    commands.spawn((
        Camera3d::default(),
        bevy::post_process::bloom::Bloom {
            intensity: 0.35,
            ..bevy::post_process::bloom::Bloom::NATURAL
        },
        Tonemapping::KhronosPbrNeutral,
        Transform::from_xyz(0.0, PLAYER_HEIGHT, 3.0).looking_at(Vec3::new(0.0, PLAYER_HEIGHT, 0.0), Vec3::Y),
        RigidBody::Kinematic,
        Collider::capsule(PLAYER_RADIUS, PLAYER_CAPSULE_LENGTH),
        Player,
        LookAngles {
            yaw: std::f32::consts::PI,
            pitch: 0.0,
        },
    ));

    // Ambient occlusion probe visualization
    let cube_handle = meshes.add(Cuboid::new(0.2, 0.2, 0.2));
    let mat_handle = materials.add(StandardMaterial {
        emissive: LinearRgba::rgb(0.0, 50.0, 0.0),
        ..default()
    });

    let weights = lightmap::unpack_probe_weights();
    for row in 0..lightmap::PROBE_GRID_ROWS {
        for col in 0..lightmap::PROBE_GRID_COLS {
            if weights[row * lightmap::PROBE_GRID_COLS + col] == 1 {
                commands.spawn((
                    Mesh3d(cube_handle.clone()),
                    MeshMaterial3d(mat_handle.clone()),
                    Transform::from_xyz(
                        50.0 + col as f32 * 0.2,
                        10.0 - row as f32 * 0.2,
                        50.0,
                    ),
                ));
            }
        }
    }
}

fn spawn_wall_colliders(commands: &mut Commands) {
    let long_wall = Collider::cuboid(
        WALL_HALF_EXTENT * 2.0 + WALL_THICKNESS,
        WALL_HEIGHT,
        WALL_THICKNESS,
    );
    let side_wall = Collider::cuboid(
        WALL_THICKNESS,
        WALL_HEIGHT,
        WALL_HALF_EXTENT * 2.0 + WALL_THICKNESS,
    );
    let y = WALL_HEIGHT * 0.5;

    for (collider, translation) in [
        (long_wall.clone(), Vec3::new(0.0, y, -WALL_HALF_EXTENT)),
        (long_wall, Vec3::new(0.0, y, WALL_HALF_EXTENT)),
        (side_wall.clone(), Vec3::new(-WALL_HALF_EXTENT, y, 0.0)),
        (side_wall, Vec3::new(WALL_HALF_EXTENT, y, 0.0)),
    ] {
        commands.spawn((
            RigidBody::Static,
            collider,
            Transform::from_translation(translation),
        ));
    }
}

fn spawn_occlusion_box(
    commands: &mut Commands,
    meshes: &mut ResMut<Assets<Mesh>>,
    materials: &mut ResMut<Assets<StandardMaterial>>,
) {
    let mat = materials.add(StandardMaterial {
        base_color: Color::BLACK,
        unlit: true,
        cull_mode: None,
        ..default()
    });

    // Slightly outside the collider walls so the GLTF scene covers them
    let extent = WALL_HALF_EXTENT + 0.5;
    // Tall enough to block the flag at y~10
    let height = 25.0;
    let thickness = 0.05;

    let long_wall_mesh = meshes.add(Cuboid::new(extent * 2.0, height, thickness));
    let side_wall_mesh = meshes.add(Cuboid::new(thickness, height, extent * 2.0));
    let cap_mesh = meshes.add(Cuboid::new(extent * 2.0, thickness, extent * 2.0));

    let y = height * 0.5;

    // Front wall (−Z)
    commands.spawn((
        Mesh3d(long_wall_mesh.clone()),
        MeshMaterial3d(mat.clone()),
        Transform::from_xyz(0.0, y, -extent),
    ));
    // Back wall (+Z)
    commands.spawn((
        Mesh3d(long_wall_mesh),
        MeshMaterial3d(mat.clone()),
        Transform::from_xyz(0.0, y, extent),
    ));
    // Left wall (−X)
    commands.spawn((
        Mesh3d(side_wall_mesh.clone()),
        MeshMaterial3d(mat.clone()),
        Transform::from_xyz(-extent, y, 0.0),
    ));
    // Right wall (+X)
    commands.spawn((
        Mesh3d(side_wall_mesh),
        MeshMaterial3d(mat.clone()),
        Transform::from_xyz(extent, y, 0.0),
    ));
    // Ceiling
    commands.spawn((
        Mesh3d(cap_mesh.clone()),
        MeshMaterial3d(mat.clone()),
        Transform::from_xyz(0.0, height, 0.0),
    ));
    // Floor
    commands.spawn((
        Mesh3d(cap_mesh),
        MeshMaterial3d(mat),
        Transform::from_xyz(0.0, -1.0, 0.0),
    ));
}

fn grab_cursor(
    mouse_buttons: Res<ButtonInput<MouseButton>>,
    keys: Res<ButtonInput<KeyCode>>,
    mut cursors: Query<&mut CursorOptions, With<PrimaryWindow>>,
) {
    let Ok(mut cursor) = cursors.single_mut() else {
        return;
    };

    if mouse_buttons.just_pressed(MouseButton::Left) {
        cursor.grab_mode = CursorGrabMode::Locked;
        cursor.visible = false;
    }

    if keys.just_pressed(KeyCode::Escape) {
        cursor.grab_mode = CursorGrabMode::None;
        cursor.visible = true;
    }
}

fn log_asset_failures(mut failures: MessageReader<UntypedAssetLoadFailedEvent>) {
    for failure in failures.read() {
        error!("Asset failed to load: {failure:?}");
    }
}

fn player_look(
    mouse_motion: Res<AccumulatedMouseMotion>,
    cursors: Query<&CursorOptions, With<PrimaryWindow>>,
    mut player: Query<(&mut Transform, &mut LookAngles), With<Player>>,
) {
    let Ok(cursor) = cursors.single() else {
        return;
    };

    if cursor.grab_mode != CursorGrabMode::Locked {
        return;
    }

    let delta = mouse_motion.delta;
    if delta == Vec2::ZERO {
        return;
    }

    let Ok((mut transform, mut look)) = player.single_mut() else {
        return;
    };

    look.yaw -= delta.x * MOUSE_SENSITIVITY;
    look.pitch = (look.pitch - delta.y * MOUSE_SENSITIVITY).clamp(-FRAC_PI_2 + 0.05, FRAC_PI_2 - 0.05);
    transform.rotation =
        Quat::from_axis_angle(Vec3::Y, look.yaw) * Quat::from_axis_angle(Vec3::X, look.pitch);
}

fn player_move(
    time: Res<Time>,
    keys: Res<ButtonInput<KeyCode>>,
    move_and_slide: MoveAndSlide,
    mut player: Query<(Entity, &Collider, &mut Transform), With<Player>>,
) {
    let Ok((entity, collider, mut transform)) = player.single_mut() else {
        return;
    };

    let mut input = Vec3::ZERO;
    if keys.pressed(KeyCode::KeyW) {
        input.z -= 1.0;
    }
    if keys.pressed(KeyCode::KeyS) {
        input.z += 1.0;
    }
    if keys.pressed(KeyCode::KeyA) {
        input.x -= 1.0;
    }
    if keys.pressed(KeyCode::KeyD) {
        input.x += 1.0;
    }

    let yaw = transform.rotation.to_euler(EulerRot::YXZ).0;
    let heading = Quat::from_axis_angle(Vec3::Y, yaw);
    let velocity = if input == Vec3::ZERO {
        Vec3::ZERO
    } else {
        heading * input.normalize() * MOVE_SPEED
    };

    let filter = SpatialQueryFilter::from_excluded_entities([entity]);
    let output = move_and_slide.move_and_slide(
        collider,
        transform.translation.adjust_precision(),
        transform.rotation.adjust_precision(),
        velocity.adjust_precision(),
        time.delta(),
        &MoveAndSlideConfig::default(),
        &filter,
        |_| MoveAndSlideHitResponse::Accept,
    );

    transform.translation = output.position;
    transform.translation.y = PLAYER_HEIGHT;
}
