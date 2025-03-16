import ExpoTorchModule from "./ExpoTorchModule";

export const { ON, OFF } = ExpoTorchModule;

export async function isTorchAvailable(): Promise<boolean> {
  return ExpoTorchModule.isTorchAvailable();
}

export async function setStateAsync(state: string) {
  return ExpoTorchModule.setStateAsync(state);
}

export async function isBrightnessControllable(): Promise<boolean> {
  return ExpoTorchModule.isBrightnessControllable();
}

export async function setBrightnessAsync(level: number) {
  if (level < 0 || level > 1) {
    throw new Error("Brightness level must be between 0 and 1");
  }
  return ExpoTorchModule.setBrightnessAsync(level);
}
