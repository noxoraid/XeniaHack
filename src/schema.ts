import {z} from "zod";

export const schema = z.object({
  title: z.string(),
  subtitle: z.string(),
  colorA: z.string(),
  colorB: z.string(),
  colorC: z.string(),
  withAudio: z.boolean(),
  volume: z.number().min(0).max(1),
});

export type Props = z.infer<typeof schema>;

export const defaults: Props = {
  title: "NOXORA",
  subtitle: "ENTER THE COSMOS",
  colorA: "#6f5bff",
  colorB: "#27d7ff",
  colorC: "#ff3fc0",
  withAudio: true,
  volume: 1,
};
