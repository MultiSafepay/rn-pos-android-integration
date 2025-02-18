import { EventEmitter } from 'expo';

const emitter = new EventEmitter({} as any);

export default {
  async setValueAsync(value: string): Promise<void> {
    // @ts-ignore
    emitter.emit('onChange', { value });
  },
};
